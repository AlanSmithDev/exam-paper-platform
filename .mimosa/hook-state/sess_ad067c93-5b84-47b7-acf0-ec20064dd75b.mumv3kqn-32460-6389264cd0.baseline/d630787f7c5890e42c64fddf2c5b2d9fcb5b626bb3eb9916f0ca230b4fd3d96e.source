package com.examforge.practice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.examforge.api.dto.QuestionSummaryDTO;
import com.examforge.api.feign.QuestionClient;
import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.Result;
import com.examforge.practice.domain.Assignment;
import com.examforge.practice.domain.AssignmentAnswer;
import com.examforge.practice.domain.AssignmentStudent;
import com.examforge.practice.logic.AnswerGrader;
import com.examforge.practice.logic.AssignmentRules;
import com.examforge.practice.mapper.AssignmentAnswerMapper;
import com.examforge.practice.mapper.AssignmentMapper;
import com.examforge.practice.mapper.AssignmentStudentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** 作业域核心服务（e 卷通第一步：布置→发布→点名→作答→批改→班级报告，docs/26 F-XKW-12） */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final AssignmentMapper assignmentMapper;
    private final AssignmentStudentMapper studentMapper;
    private final AssignmentAnswerMapper answerMapper;
    private final QuestionClient questionClient;
    private final com.examforge.api.feign.AnswerSheetClient answerSheetClient;

    // ---------- 教师侧 ----------

    /** 布置作业（草稿）：题目必须真实存在（批量校验） */
    public Map<String, Object> create(Long teacherId, String title, Long subjectId,
                                      List<Long> questionIds, Integer deadlineHours) {
        if (title == null || title.isBlank()) throw new BizException(Result.BAD_REQUEST, "作业标题必填");
        if (questionIds == null || questionIds.isEmpty()) throw new BizException(Result.BAD_REQUEST, "题目不能为空");
        List<QuestionSummaryDTO> found = questionClient.listByIds(questionIds);
        if (found.size() != questionIds.size()) throw new BizException(Result.BAD_REQUEST, "存在无效题目，请检查题目列表");

        Assignment a = new Assignment();
        a.setTeacherId(teacherId);
        a.setTitle(title.trim());
        a.setSubjectId(subjectId);
        a.setQuestionIds(questionIds.toString());
        a.setDeadline(deadlineHours == null || deadlineHours <= 0 ? null : LocalDateTime.now().plusHours(deadlineHours));
        a.setStatus(AssignmentRules.DRAFT);
        a.setCreatedAt(LocalDateTime.now());
        assignmentMapper.insert(a);
        return Map.of("assignmentId", a.getId(), "count", questionIds.size(),
                "deadline", a.getDeadline() == null ? "" : a.getDeadline().toString());
    }

    /** 发布：条件状态迁移防并发（未发布不可被学生看到） */
    public Map<String, Object> publish(Long teacherId, Long assignmentId) {
        Assignment a = owned(teacherId, assignmentId);
        if (assignmentMapper.transition(assignmentId, AssignmentRules.DRAFT, AssignmentRules.PUBLISHED) == 0) {
            throw new BizException(Result.BAD_REQUEST, "仅草稿可发布（当前状态: " + a.getStatus() + "）");
        }
        return Map.of("ok", true, "status", AssignmentRules.PUBLISHED);
    }

    /** 关闭/作废 */
    public Map<String, Object> close(Long teacherId, Long assignmentId) {
        owned(teacherId, assignmentId);
        int from = assignmentMapper.selectById(assignmentId).getStatus();
        AssignmentRules.mustTransition(from, AssignmentRules.CLOSED);
        if (assignmentMapper.transition(assignmentId, from, AssignmentRules.CLOSED) == 0) {
            throw new BizException(Result.BAD_REQUEST, "状态已变化，请刷新");
        }
        return Map.of("ok", true, "status", AssignmentRules.CLOSED);
    }

    /** 点名（幂等）：仅已发布作业可指派 */
    @Transactional
    public Map<String, Object> assign(Long teacherId, Long assignmentId, List<Long> studentIds) {
        Assignment a = owned(teacherId, assignmentId);
        if (a.getStatus() != AssignmentRules.PUBLISHED) throw new BizException(Result.BAD_REQUEST, "请先发布作业再点名");
        if (studentIds == null || studentIds.isEmpty()) throw new BizException(Result.BAD_REQUEST, "学生名单不能为空");
        int added = 0;
        for (Long sid : studentIds.stream().distinct().toList()) {
            added += studentMapper.insertIgnore(assignmentId, sid);
        }
        long roster = studentMapper.selectCount(new LambdaQueryWrapper<AssignmentStudent>()
                .eq(AssignmentStudent::getAssignmentId, assignmentId));
        log.info("作业点名: assignment={} 新增{}人 名单共{}人", assignmentId, added, roster);
        return Map.of("added", added, "roster", roster);
    }

    public List<Assignment> myTeaching(Long teacherId) {
        return assignmentMapper.selectList(new LambdaQueryWrapper<Assignment>()
                .eq(Assignment::getTeacherId, teacherId).orderByDesc(Assignment::getId));
    }

    // ---------- 学生侧 ----------

    /** 我的作业（待完成/已提交） */
    public List<Map<String, Object>> studentView(Long studentId) {
        List<AssignmentStudent> rows = studentMapper.selectList(new LambdaQueryWrapper<AssignmentStudent>()
                .eq(AssignmentStudent::getStudentId, studentId).orderByDesc(AssignmentStudent::getAssignmentId));
        if (rows.isEmpty()) return List.of();
        Map<Long, AssignmentStudent> byAssignment = rows.stream()
                .collect(Collectors.toMap(AssignmentStudent::getAssignmentId, r -> r));
        List<Assignment> assignments = assignmentMapper.selectBatchIds(byAssignment.keySet());
        return assignments.stream().filter(a -> a.getStatus() != AssignmentRules.DRAFT)
                .map(a -> {
                    AssignmentStudent r = byAssignment.get(a.getId());
                    return Map.<String, Object>of(
                            "assignmentId", a.getId(), "title", a.getTitle(),
                            "status", a.getStatus() == AssignmentRules.CLOSED ? "CLOSED" : "OPEN",
                            "myStatus", r.getStatus(),
                            "deadline", a.getDeadline() == null ? "" : a.getDeadline().toString(),
                            "late", r.getLate() == 1,
                            "score", r.getScore() == null ? -1 : r.getScore());
                }).toList();
    }

    /** 学生取作业题目（脱敏：不含答案/解析；answerable=可作答，已提交/已关闭仍可回看题目） */
    public Map<String, Object> questionsForStudent(Long studentId, Long assignmentId) {
        Assignment a = assignmentMapper.selectById(assignmentId);
        if (a == null || a.getStatus() == AssignmentRules.DRAFT) {
            throw new BizException(Result.NOT_FOUND, "作业不存在或未开放");
        }
        AssignmentStudent row = studentMapper.selectOne(new LambdaQueryWrapper<AssignmentStudent>()
                .eq(AssignmentStudent::getAssignmentId, assignmentId)
                .eq(AssignmentStudent::getStudentId, studentId));
        if (row == null) throw new BizException(Result.FORBIDDEN, "你不在本作业名单中");

        List<Long> qids = parseQuestionIds(a.getQuestionIds());
        Map<Long, QuestionSummaryDTO> byId = new HashMap<>();
        for (QuestionSummaryDTO q : questionClient.listByIds(qids)) byId.put(q.getId(), q);
        List<Map<String, Object>> questions = qids.stream().map(byId::get).filter(Objects::nonNull)
                .map(q -> Map.<String, Object>of(
                        "questionId", q.getId(),
                        "type", q.getType() == null ? "" : q.getType(),
                        "stem", q.getStem() == null ? "" : q.getStem(),
                        "options", q.getOptions() == null ? "" : q.getOptions()))
                .toList();
        boolean answerable = a.getStatus() == AssignmentRules.PUBLISHED
                && row.getStatus() == AssignmentRules.ST_ASSIGNED;
        return Map.of("assignmentId", assignmentId, "title", a.getTitle(),
                "deadline", a.getDeadline() == null ? "" : a.getDeadline().toString(),
                "answerable", answerable,
                "alreadySubmitted", row.getStatus() != AssignmentRules.ST_ASSIGNED,
                "questions", questions);
    }

    /** 学生作答：名单校验 → 批量判分 → 明细落库 → 名单状态置 SUBMITTED（超时标记 late） */
    @Transactional
    public Map<String, Object> submit(Long studentId, Long assignmentId, List<Map<String, Object>> answers) {
        Assignment a = assignmentMapper.selectById(assignmentId);
        if (a == null || a.getStatus() != AssignmentRules.PUBLISHED) {
            throw new BizException(Result.NOT_FOUND, "作业不存在或未开放作答");
        }
        AssignmentStudent row = studentMapper.selectOne(new LambdaQueryWrapper<AssignmentStudent>()
                .eq(AssignmentStudent::getAssignmentId, assignmentId).eq(AssignmentStudent::getStudentId, studentId));
        if (row == null) throw new BizException(Result.FORBIDDEN, "你不在本作业名单中");
        if (row.getStatus() != AssignmentRules.ST_ASSIGNED) throw new BizException(Result.BAD_REQUEST, "已提交过，等待教师批改");

        List<Long> qids = parseQuestionIds(a.getQuestionIds());
        List<QuestionSummaryDTO> questions = questionClient.listByIds(qids);
        Map<Long, QuestionSummaryDTO> byId = questions.stream()
                .collect(Collectors.toMap(QuestionSummaryDTO::getId, q -> q));

        int total = 0, correctObjective = 0, pendingManual = 0;
        List<Map<String, Object>> detail = new ArrayList<>();
        Map<Long, String> userAnswers = new LinkedHashMap<>();
        for (Map<String, Object> ans : answers) {
            Long qid = Long.valueOf(String.valueOf(ans.get("questionId")));
            QuestionSummaryDTO q = byId.get(qid);
            if (q == null) continue;                                  // 非本卷题忽略
            String user = String.valueOf(ans.getOrDefault("answer", ""));
            int dur = ans.get("durationMs") == null ? 0 : Integer.parseInt(String.valueOf(ans.get("durationMs")));
            Boolean right = AnswerGrader.grade(AnswerGrader.kindOf(q.getType()), q.getAnswer(), user);
            total++;
            if (right == null) pendingManual++;
            else if (right) correctObjective++;

            AssignmentAnswer aa = new AssignmentAnswer();
            aa.setAssignmentId(assignmentId);
            aa.setStudentId(studentId);
            aa.setQuestionId(qid);
            aa.setAnswer(user);
            aa.setCorrect(right == null ? null : (right ? 1 : 0));
            aa.setDurationMs(dur);
            aa.setCreatedAt(LocalDateTime.now());
            try {
                answerMapper.insert(aa);
            } catch (DuplicateKeyException e) {
                throw new BizException(Result.BAD_REQUEST, "请勿重复提交");
            }
            userAnswers.put(qid, user);
            detail.add(Map.of("questionId", qid, "correct", right == null ? "PENDING" : right));
        }
        if (total == 0) throw new BizException(Result.BAD_REQUEST, "未提交任何有效作答");

        boolean late = AssignmentRules.late(a.getDeadline(), LocalDateTime.now());
        AssignmentRules.ScoreCalc calc = AssignmentRules.calcScore(total, correctObjective, pendingManual);
        row.setStatus(AssignmentRules.ST_SUBMITTED);
        row.setScore(BigDecimal.valueOf(calc.correctRatePct()));
        row.setLate(late ? 1 : 0);
        row.setSubmitAt(LocalDateTime.now());
        studentMapper.updateById(row);
        log.info("作业提交: assignment={} student={} {}/{} 待批{} late={}",
                assignmentId, studentId, correctObjective, total, pendingManual, late);
        return Map.of("total", total, "correctObjective", correctObjective,
                "pendingManual", pendingManual, "correctRatePct", calc.correctRatePct(), "late", late);
    }

    // ---------- 批改与报告 ----------

    /** 教师批改解答题：逐题给 correct → 重算总正确率，名单置 GRADED（全部批完才置） */
    @Transactional
    public Map<String, Object> grade(Long teacherId, Long assignmentId, Long studentId,
                                     List<Map<String, Object>> items) {
        Assignment a = owned(teacherId, assignmentId);
        AssignmentStudent row = studentMapper.selectOne(new LambdaQueryWrapper<AssignmentStudent>()
                .eq(AssignmentStudent::getAssignmentId, assignmentId).eq(AssignmentStudent::getStudentId, studentId));
        if (row == null || row.getStatus() != AssignmentRules.ST_SUBMITTED) {
            throw new BizException(Result.BAD_REQUEST, "该学生作业未处于待批改状态");
        }
        List<AssignmentAnswer> answers = answerMapper.selectList(new LambdaQueryWrapper<AssignmentAnswer>()
                .eq(AssignmentAnswer::getAssignmentId, assignmentId).eq(AssignmentAnswer::getStudentId, studentId));
        Set<Long> graded = new HashSet<>();
        for (Map<String, Object> item : items) {
            Long qid = Long.valueOf(String.valueOf(item.get("questionId")));
            Object c = item.get("correct");
            if (c == null) continue;
            boolean right = "1".equals(String.valueOf(c)) || "true".equalsIgnoreCase(String.valueOf(c));
            for (AssignmentAnswer ans : answers) {
                if (ans.getQuestionId().equals(qid)) {
                    ans.setCorrect(right ? 1 : 0);
                    if (item.get("score") != null) ans.setScore(BigDecimal.valueOf(Double.parseDouble(String.valueOf(item.get("score")))));
                    answerMapper.updateById(ans);
                    graded.add(qid);
                }
            }
        }
        long stillPending = answerMapper.selectCount(new LambdaQueryWrapper<AssignmentAnswer>()
                .eq(AssignmentAnswer::getAssignmentId, assignmentId)
                .eq(AssignmentAnswer::getStudentId, studentId)
                .isNull(AssignmentAnswer::getCorrect));
        if (stillPending == 0) {
            long correct = answerMapper.selectCount(new LambdaQueryWrapper<AssignmentAnswer>()
                    .eq(AssignmentAnswer::getAssignmentId, assignmentId)
                    .eq(AssignmentAnswer::getStudentId, studentId)
                    .eq(AssignmentAnswer::getCorrect, 1));
            int totalCount = parseQuestionIds(a.getQuestionIds()).size();
            AssignmentRules.ScoreCalc calc = AssignmentRules.calcScore(totalCount, (int) correct, 0);
            row.setStatus(AssignmentRules.ST_GRADED);
            row.setScore(BigDecimal.valueOf(calc.correctRatePct()));
            row.setGradedAt(LocalDateTime.now());
            studentMapper.updateById(row);
            return Map.of("graded", graded.size(), "allDone", true, "finalRatePct", calc.correctRatePct());
        }
        return Map.of("graded", graded.size(), "allDone", false, "stillPending", stillPending);
    }

    /** 班级报告：名单概览 + 逐题正确率 + 薄弱知识点（教师视角） */
    public Map<String, Object> classReport(Long teacherId, Long assignmentId) {        Assignment a = owned(teacherId, assignmentId);
        List<AssignmentStudent> roster = studentMapper.selectList(new LambdaQueryWrapper<AssignmentStudent>()
                .eq(AssignmentStudent::getAssignmentId, assignmentId));
        long submitted = roster.stream().filter(r -> r.getStatus() >= AssignmentRules.ST_SUBMITTED).count();
        long graded = roster.stream().filter(r -> r.getStatus() == AssignmentRules.ST_GRADED).count();
        double avgScore = roster.stream().filter(r -> r.getScore() != null)
                .mapToDouble(r -> r.getScore().doubleValue()).average().orElse(0);

        List<AssignmentAnswer> answers = answerMapper.selectList(new LambdaQueryWrapper<AssignmentAnswer>()
                .eq(AssignmentAnswer::getAssignmentId, assignmentId));
        Map<Long, int[]> perQuestion = new LinkedHashMap<>();   // [correct, wrong, pending]
        for (AssignmentAnswer ans : answers) {
            int[] arr = perQuestion.computeIfAbsent(ans.getQuestionId(), k -> new int[3]);
            if (ans.getCorrect() == null) arr[2]++;
            else if (ans.getCorrect() == 1) arr[0]++;
            else arr[1]++;
        }
        List<Map<String, Object>> questionStats = perQuestion.entrySet().stream()
                .map(e -> Map.<String, Object>of("questionId", e.getKey(),
                        "correct", e.getValue()[0], "wrong", e.getValue()[1], "pending", e.getValue()[2]))
                .toList();

        // 薄弱知识点：按题聚合（题的 kpNames + 该题班级正确率）
        List<Long> qids = parseQuestionIds(a.getQuestionIds());
        Map<Long, QuestionSummaryDTO> qMap = qids.isEmpty() ? Map.of()
                : questionClient.listByIds(qids).stream().collect(Collectors.toMap(QuestionSummaryDTO::getId, q -> q));
        List<AssignmentRules.KpRow> kpRows = perQuestion.entrySet().stream()
                .map(e -> {
                    QuestionSummaryDTO q = qMap.get(e.getKey());
                    int[] arr = e.getValue();
                    boolean mostlyCorrect = arr[0] >= arr[1];
                    return new AssignmentRules.KpRow(q == null ? "" : q.getKpNames(), mostlyCorrect);
                }).toList();
        List<Map<String, Object>> weakKp = AssignmentRules.kpAggregate(kpRows);

        return Map.of("assignmentId", assignmentId, "title", a.getTitle(),
                "roster", roster.size(), "submitted", submitted, "graded", graded,
                "avgScorePct", Math.round(avgScore * 100) / 100.0,
                "questionStats", questionStats, "weakKnowledgePoints", weakKp);
    }

    /** 作业答题卡（e 卷通二阶段）：教师为整卷作业一键生成（题目顺序=布置顺序） */
    public Map<String, Object> answerSheet(Long teacherId, Long assignmentId) {
        Assignment a = owned(teacherId, assignmentId);
        return answerSheetClient.answerSheet(Map.of(
                "title", a.getTitle(),
                "refId", String.valueOf(assignmentId),
                "questionIds", parseQuestionIds(a.getQuestionIds())));
    }

    // ---------- 内部 ----------

    private Assignment owned(Long teacherId, Long assignmentId) {
        Assignment a = assignmentMapper.selectById(assignmentId);
        if (a == null || !a.getTeacherId().equals(teacherId)) throw new BizException(Result.NOT_FOUND, "作业不存在");
        return a;
    }

    private List<Long> parseQuestionIds(String json) {
        if (json == null || json.isBlank()) return List.of();
        return Arrays.stream(json.replaceAll("[\\[\\] ]", "").split(","))
                .filter(s -> !s.isBlank()).map(Long::valueOf).toList();
    }
}
