package com.examforge.practice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.examforge.api.dto.QuestionSummaryDTO;
import com.examforge.api.feign.QuestionClient;
import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.Result;
import com.examforge.practice.domain.Assignment;
import com.examforge.practice.domain.AssignmentAnswer;
import com.examforge.practice.domain.AssignmentStudent;
import com.examforge.practice.domain.ScanRecord;
import com.examforge.practice.logic.AnswerGrader;
import com.examforge.practice.logic.AssignmentRules;
import com.examforge.practice.logic.ScanRules;
import com.examforge.practice.mapper.AssignmentAnswerMapper;
import com.examforge.practice.mapper.AssignmentMapper;
import com.examforge.practice.mapper.AssignmentStudentMapper;
import com.examforge.practice.mapper.ScanRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 扫描阅卷服务（e 卷通二阶段：扫描件=批改证据层 → 识别结果导入落账，docs/26 §7） */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScanService {

    private final AssignmentMapper assignmentMapper;
    private final AssignmentStudentMapper studentMapper;
    private final ScanRecordMapper scanMapper;
    private final AssignmentAnswerMapper answerMapper;
    private final QuestionClient questionClient;
    private final com.examforge.api.feign.OcrClient ocrClient;
    private final WrongBookSync wrongBookSync;

    private Path baseDir() {
        return Path.of(System.getProperty("java.io.tmpdir"), "examforge-scans");
    }

    /** 教师上传学生扫描件：本人作业 + 名单校验 + 扩展名/大小校验 → 落盘 + 记录 UPLOADED */
    public Map<String, Object> upload(Long teacherId, Long assignmentId, Long studentId, MultipartFile file) {
        Assignment a = owned(teacherId, assignmentId);
        AssignmentStudent row = studentMapper.selectOne(new LambdaQueryWrapper<AssignmentStudent>()
                .eq(AssignmentStudent::getAssignmentId, assignmentId)
                .eq(AssignmentStudent::getStudentId, studentId));
        if (row == null) throw new BizException(Result.BAD_REQUEST, "该学生不在作业名单中");
        if (file == null || file.isEmpty()) throw new BizException(Result.BAD_REQUEST, "扫描件为空");
        ScanRules.validateSize(file.getSize());
        String ext = ScanRules.validateExt(file.getOriginalFilename());

        String relative = ScanRules.storagePath(assignmentId, studentId, System.currentTimeMillis(), ext);
        Path target = baseDir().resolve(relative).normalize();
        if (!target.startsWith(baseDir())) throw new BizException(Result.BAD_REQUEST, "存储路径非法");
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target.toFile());
        } catch (Exception e) {
            throw new BizException(Result.SYSTEM, "扫描件保存失败");
        }

        ScanRecord r = new ScanRecord();
        r.setAssignmentId(assignmentId);
        r.setStudentId(studentId);
        r.setFileName(file.getOriginalFilename());
        r.setStoredPath(relative);
        r.setMime(file.getContentType() == null ? "application/octet-stream" : file.getContentType());
        r.setSizeBytes((int) file.getSize());
        r.setStatus(ScanRules.ST_UPLOADED);
        r.setUploadedBy(teacherId);
        r.setCreatedAt(LocalDateTime.now());
        scanMapper.insert(r);
        log.info("扫描件上传: assignment={} student={} scan={} {}B", assignmentId, studentId, r.getId(), r.getSizeBytes());
        return Map.of("scanId", r.getId(), "storedPath", relative, "status", r.getStatus());
    }

    /** 某作业全部扫描件（教师视角，可按学生过滤） */
    public List<ScanRecord> list(Long teacherId, Long assignmentId, Long studentId) {
        owned(teacherId, assignmentId);
        return scanMapper.selectList(new LambdaQueryWrapper<ScanRecord>()
                .eq(ScanRecord::getAssignmentId, assignmentId)
                .eq(studentId != null, ScanRecord::getStudentId, studentId)
                .orderByDesc(ScanRecord::getId));
    }

    /** 扫描件文件流（教师本人校验，供前端 <img>/预览） */
    public Map<String, Object> file(Long teacherId, Long assignmentId, Long scanId) {
        owned(teacherId, assignmentId);
        ScanRecord r = scanMapper.selectById(scanId);
        if (r == null || !r.getAssignmentId().equals(assignmentId)) {
            throw new BizException(Result.NOT_FOUND, "扫描件不存在");
        }
        try {
            Path p = baseDir().resolve(r.getStoredPath()).normalize();
            if (!p.startsWith(baseDir())) throw new BizException(Result.BAD_REQUEST, "存储路径非法");
            return Map.of("bytes", Files.readAllBytes(p), "mime", r.getMime(), "fileName", r.getFileName());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(Result.NOT_FOUND, "扫描件文件已丢失");
        }
    }

    /** 批改名单（含每人扫描件数与作业状态） */
    public List<Map<String, Object>> roster(Long teacherId, Long assignmentId) {
        owned(teacherId, assignmentId);
        List<AssignmentStudent> roster = studentMapper.selectList(new LambdaQueryWrapper<AssignmentStudent>()
                .eq(AssignmentStudent::getAssignmentId, assignmentId).orderByAsc(AssignmentStudent::getStudentId));
        List<ScanRecord> scans = scanMapper.selectList(new LambdaQueryWrapper<ScanRecord>()
                .eq(ScanRecord::getAssignmentId, assignmentId));
        return roster.stream().map(r -> Map.<String, Object>of(
                "studentId", r.getStudentId(),
                "status", r.getStatus(),
                "late", r.getLate() != null && r.getLate() == 1,
                "scanCount", scans.stream().filter(s -> s.getStudentId().equals(r.getStudentId())).count())).toList();
    }

    /** 识别结果落账（P3 AI 视觉钩子：MOCK 走人工转录，接口先行） */
    public Map<String, Object> recognize(Long teacherId, Long assignmentId, Long scanId, String ocrJson) {
        owned(teacherId, assignmentId);
        ScanRecord r = scanMapper.selectById(scanId);
        if (r == null || !r.getAssignmentId().equals(assignmentId)) {
            throw new BizException(Result.NOT_FOUND, "扫描件不存在");
        }
        ScanRules.mustTransition(r.getStatus(), ScanRules.ST_RECOGNIZED);
        r.setStatus(ScanRules.ST_RECOGNIZED);
        r.setOcrJson(ocrJson == null ? "[]" : ocrJson);
        scanMapper.updateById(r);
        return Map.of("ok", true, "status", r.getStatus());
    }

    /** AI 视觉识别（P3 钩子）：扫描件→base64→AI OCR；未识别时保持 UPLOADED，人工转录流程不断 */
    public Map<String, Object> recognizeByAi(Long teacherId, Long assignmentId, Long scanId) {
        Assignment a = owned(teacherId, assignmentId);
        ScanRecord r = scanMapper.selectById(scanId);
        if (r == null || !r.getAssignmentId().equals(assignmentId)) {
            throw new BizException(Result.NOT_FOUND, "扫描件不存在");
        }
        try {
            ScanRules.mustTransition(r.getStatus(), ScanRules.ST_RECOGNIZED);
        } catch (IllegalStateException e) {
            throw new BizException(Result.BAD_REQUEST, e.getMessage());
        }
        Path p = baseDir().resolve(r.getStoredPath()).normalize();
        if (!p.startsWith(baseDir())) throw new BizException(Result.BAD_REQUEST, "存储路径非法");
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(p);
        } catch (Exception e) {
            throw new BizException(Result.NOT_FOUND, "扫描件文件已丢失");
        }
        String b64 = java.util.Base64.getEncoder().encodeToString(bytes);
        Map<String, Object> out = ocrClient.ocr(Map.of(
                "userId", String.valueOf(teacherId),
                "imageBase64", b64,
                "mime", r.getMime() == null ? "image/jpeg" : r.getMime(),
                "questionIds", parseQuestionIds(a.getQuestionIds())));
        if (Boolean.TRUE.equals(out.get("recognized"))) {
            r.setStatus(ScanRules.ST_RECOGNIZED);
            r.setOcrJson(String.valueOf(out.get("raw")));
            scanMapper.updateById(r);
        }
        return out;   // recognized=false 时保持 UPLOADED，人工转录流程不断
    }

    /**
     * 识别结果导入落账（e 卷通二阶段三期，docs/26 §7）：RECOGNIZED → IMPORTED。
     * 解析 ocr_json（{"answers":[{questionId,answer}...]}）按题 upsert 作答 + 客观题自动判分（AnswerGrader，
     * 解答题置待人工）+ 名单状态/正确率落账（判分分母=作业题目数，与教师批改 grade() 同口径）。
     * ocrJson 传参可覆盖扫描件已存结果（人工转录/教师修正场景）；与在线提交作答按题合并、扫描值覆盖。
     */
    @Transactional
    public Map<String, Object> importScan(Long teacherId, Long assignmentId, Long scanId, String ocrJson) {
        Assignment a = owned(teacherId, assignmentId);
        ScanRecord r = scanMapper.selectById(scanId);
        if (r == null || !r.getAssignmentId().equals(assignmentId)) {
            throw new BizException(Result.NOT_FOUND, "扫描件不存在");
        }
        if (r.getStudentId() == null) throw new BizException(Result.BAD_REQUEST, "扫描件未关联学生");
        String json = ocrJson == null || ocrJson.isBlank() ? r.getOcrJson() : ocrJson;
        List<Map<String, Object>> entries;
        try {
            entries = ScanRules.parseAnswers(json);
        } catch (IllegalArgumentException e) {
            throw new BizException(Result.BAD_REQUEST, e.getMessage());
        }
        try {
            ScanRules.mustTransition(r.getStatus(), ScanRules.ST_IMPORTED);
        } catch (IllegalStateException e) {
            throw new BizException(Result.BAD_REQUEST, e.getMessage());
        }
        AssignmentStudent row = studentMapper.selectOne(new LambdaQueryWrapper<AssignmentStudent>()
                .eq(AssignmentStudent::getAssignmentId, assignmentId)
                .eq(AssignmentStudent::getStudentId, r.getStudentId()));
        if (row == null) throw new BizException(Result.BAD_REQUEST, "该学生不在作业名单中");

        List<Long> qids = entries.stream().map(m -> (Long) m.get("questionId")).distinct().toList();
        Map<Long, QuestionSummaryDTO> byId = questionClient.listByIds(qids).stream()
                .collect(Collectors.toMap(QuestionSummaryDTO::getId, q -> q));

        Long studentId = r.getStudentId();
        int imported = 0, correctObjective = 0, pendingManual = 0;
        for (Map<String, Object> en : entries) {
            Long qid = (Long) en.get("questionId");
            QuestionSummaryDTO q = byId.get(qid);
            if (q == null) continue;                       // 非本卷题忽略（与 submit 同策略）
            String user = String.valueOf(en.get("answer"));
            Boolean right = AnswerGrader.grade(AnswerGrader.kindOf(q.getType()), q.getAnswer(), user);
            AssignmentAnswer aa = answerMapper.selectOne(new LambdaQueryWrapper<AssignmentAnswer>()
                    .eq(AssignmentAnswer::getAssignmentId, assignmentId)
                    .eq(AssignmentAnswer::getStudentId, studentId)
                    .eq(AssignmentAnswer::getQuestionId, qid));
            boolean isNew = aa == null;
            if (isNew) {
                aa = new AssignmentAnswer();
                aa.setAssignmentId(assignmentId);
                aa.setStudentId(studentId);
                aa.setQuestionId(qid);
                aa.setCreatedAt(LocalDateTime.now());
            }
            Integer prev = isNew ? null : aa.getCorrect();      // 旧判分值：错题本差分联动依据
            aa.setAnswer(user);
            aa.setCorrect(right == null ? null : (right ? 1 : 0));
            try {
                if (isNew) answerMapper.insert(aa); else answerMapper.updateById(aa);
            } catch (DuplicateKeyException e) {            // 并发兜底：已存在则改走更新
                AssignmentAnswer exist = answerMapper.selectOne(new LambdaQueryWrapper<AssignmentAnswer>()
                        .eq(AssignmentAnswer::getAssignmentId, assignmentId)
                        .eq(AssignmentAnswer::getStudentId, studentId)
                        .eq(AssignmentAnswer::getQuestionId, qid));
                if (exist != null) { prev = exist.getCorrect(); aa.setId(exist.getId()); answerMapper.updateById(aa); }
            }
            wrongBookSync.sync(studentId, qid, q.getKpNames(), prev, aa.getCorrect());   // 考后诊断：错题本差分联动（修正导入对→错/错→对均正确入本或解决）
            imported++;
            if (right == null) pendingManual++;
            else if (right) correctObjective++;
        }
        if (imported == 0) throw new BizException(Result.BAD_REQUEST, "识别结果与本卷题目无匹配");

        // 名单状态/正确率：全部题目已判分 → GRADED；否则扫描导入视作提交，解答题等人工批改
        long totalCount = parseQuestionIds(a.getQuestionIds()).size();
        long correct = answerMapper.selectCount(new LambdaQueryWrapper<AssignmentAnswer>()
                .eq(AssignmentAnswer::getAssignmentId, assignmentId)
                .eq(AssignmentAnswer::getStudentId, studentId)
                .eq(AssignmentAnswer::getCorrect, 1));
        long pending = answerMapper.selectCount(new LambdaQueryWrapper<AssignmentAnswer>()
                .eq(AssignmentAnswer::getAssignmentId, assignmentId)
                .eq(AssignmentAnswer::getStudentId, studentId)
                .isNull(AssignmentAnswer::getCorrect));
        if (pending == 0 && totalCount > 0) {
            AssignmentRules.ScoreCalc calc = AssignmentRules.calcScore((int) totalCount, (int) correct, 0);
            row.setStatus(AssignmentRules.ST_GRADED);
            row.setScore(BigDecimal.valueOf(calc.correctRatePct()));
            row.setGradedAt(LocalDateTime.now());
        } else if (row.getStatus() == AssignmentRules.ST_ASSIGNED) {
            row.setStatus(AssignmentRules.ST_SUBMITTED);
        }
        studentMapper.updateById(row);

        r.setStatus(ScanRules.ST_IMPORTED);
        if (ocrJson != null && !ocrJson.isBlank()) r.setOcrJson(json);
        scanMapper.updateById(r);
        log.info("扫描识别导入: assignment={} student={} scan={} imported={} pending={}",
                assignmentId, studentId, scanId, imported, pending);
        return Map.of("imported", imported, "correctObjective", correctObjective,
                "pendingManual", pendingManual, "total", totalCount,
                "studentStatus", row.getStatus(), "graded", pending == 0);
    }

    private List<Long> parseQuestionIds(String json) {
        if (json == null || json.isBlank()) return List.of();
        return java.util.Arrays.stream(json.replaceAll("[\\[\\] ]", "").split(","))
                .filter(s -> !s.isBlank()).map(Long::valueOf).toList();
    }

    private Assignment owned(Long teacherId, Long assignmentId) {
        Assignment a = assignmentMapper.selectById(assignmentId);
        if (a == null || !a.getTeacherId().equals(teacherId)) throw new BizException(Result.NOT_FOUND, "作业不存在");
        if (a.getStatus() == AssignmentRules.CLOSED) throw new BizException(Result.BAD_REQUEST, "作业已关闭");
        return a;
    }
}
