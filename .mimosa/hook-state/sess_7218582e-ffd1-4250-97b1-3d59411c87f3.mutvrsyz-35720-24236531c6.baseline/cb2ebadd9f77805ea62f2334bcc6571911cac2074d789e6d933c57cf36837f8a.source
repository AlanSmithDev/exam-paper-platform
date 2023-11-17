package com.examforge.practice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.examforge.api.dto.QuestionSummaryDTO;
import com.examforge.api.feign.QuestionClient;
import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.Result;
import com.examforge.practice.domain.Practice;
import com.examforge.practice.domain.PracticeAnswer;
import com.examforge.practice.domain.WrongQuestion;
import com.examforge.practice.logic.AnswerGrader;
import com.examforge.practice.mapper.PracticeAnswerMapper;
import com.examforge.practice.mapper.PracticeMapper;
import com.examforge.practice.mapper.WrongQuestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** 练习/错题核心服务（docs/15 PR-1~PR-6） */
@Service
@RequiredArgsConstructor
public class PracticeService {

    private final PracticeMapper practiceMapper;
    private final PracticeAnswerMapper answerMapper;
    private final WrongQuestionMapper wrongMapper;
    private final QuestionClient questionClient;

    /** PR-1 生成练习：按知识点/题型拉题 N 道 */
    public Map<String, Object> create(Long uid, Long subjectId, String kp, String type, int count) {
        List<QuestionSummaryDTO> pool = questionClient.listByKp(subjectId, kp, type, Math.max(count, 1) * 2);
        Collections.shuffle(pool);
        List<QuestionSummaryDTO> picked = pool.stream().limit(Math.max(1, Math.min(count, 20))).toList();
        if (picked.isEmpty()) throw new BizException(Result.NOT_FOUND, "该条件下暂无题目");

        Practice p = new Practice();
        p.setUserId(uid);
        p.setSubjectId(subjectId);
        p.setMode("KP");
        p.setQuestionIds(picked.stream().map(q -> String.valueOf(q.getId())).toList().toString());
        p.setCreatedAt(LocalDateTime.now());
        practiceMapper.insert(p);
        return Map.of("practiceId", p.getId(), "count", picked.size(),
                "questions", picked.stream().map(q -> Map.of("id", q.getId(), "type", q.getType(),
                        "difficulty", q.getDifficulty(), "stem", q.getStem())).toList());
    }

    /** PR-5 错题再练卷：取未解决错题（≤count）生成 mode=WRONG 练习 */
    public Map<String, Object> createFromWrong(Long uid, int count) {
        List<WrongQuestion> wrongs = wrongMapper.selectList(new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getUserId, uid).eq(WrongQuestion::getResolved, 0)
                .orderByDesc(WrongQuestion::getLastWrongAt)
                .last("LIMIT " + Math.max(1, Math.min(count, 20))));
        if (wrongs.isEmpty()) throw new BizException(Result.NOT_FOUND, "没有待解决的错题，太棒了！");

        Practice p = new Practice();
        p.setUserId(uid);
        p.setMode("WRONG");
        p.setQuestionIds(wrongs.stream().map(w -> String.valueOf(w.getQuestionId())).toList().toString());
        p.setCreatedAt(LocalDateTime.now());
        practiceMapper.insert(p);
        return Map.of("practiceId", p.getId(), "count", wrongs.size(),
                "mode", "WRONG", "questionIds", wrongs.stream().map(WrongQuestion::getQuestionId).toList());
    }

    /** PR-2/PR-3/PR-4 提交判分 + 错题入库 */
    public Map<String, Object> submit(Long uid, Long practiceId, List<Map<String, Object>> answers) {
        Practice p = practiceMapper.selectById(practiceId);
        if (p == null || !p.getUserId().equals(uid)) throw new BizException(Result.NOT_FOUND, "练习不存在");
        // 拉标准答案（Feign 逐题，M2 改批量）
        Map<Long, QuestionSummaryDTO> std = new HashMap<>();
        for (Object qid : p.getQuestionIds().replaceAll("[\\[\\] ]", "").split(",")) {
            if (qid.toString().isBlank()) continue;
            QuestionSummaryDTO q = questionClient.getById(Long.valueOf(qid.toString()));
            if (q != null) std.put(q.getId(), q);
        }

        int total = 0, correct = 0;
        List<Map<String, Object>> detail = new ArrayList<>();
        for (Map<String, Object> a : answers) {
            long qid = Long.parseLong(String.valueOf(a.get("questionId")));
            String user = String.valueOf(a.getOrDefault("answer", ""));
            int dur = a.get("durationMs") == null ? 0 : Integer.parseInt(String.valueOf(a.get("durationMs")));
            QuestionSummaryDTO q = std.get(qid);
            if (q == null) continue;
            AnswerGrader.Kind kind = AnswerGrader.kindOf(q.getType());
            Boolean right = AnswerGrader.grade(kind, q.getAnswer(), user);
            total++;
            if (Boolean.TRUE.equals(right)) correct++;

            PracticeAnswer pa = new PracticeAnswer();
            pa.setPracticeId(practiceId);
            pa.setQuestionId(qid);
            pa.setAnswer(user);
            pa.setCorrect(right == null ? null : (right ? 1 : 0));
            pa.setDurationMs(dur);
            pa.setCreatedAt(LocalDateTime.now());
            answerMapper.insert(pa);

            if (Boolean.FALSE.equals(right)) wrongMapper.upsertWrong(uid, qid, null);
            if (Boolean.TRUE.equals(right)) resolveWrong(uid, qid);

            Map<String, Object> d = new HashMap<>();
            d.put("questionId", qid);
            d.put("correct", right);
            d.put("standard", q.getAnswer());
            detail.add(d);
        }
        return Map.of("total", total, "correct", correct,
                "correctRate", total == 0 ? 0 : Math.round(correct * 1000.0 / total) / 1000.0,
                "detail", detail);
    }

    private void resolveWrong(Long uid, Long qid) {
        WrongQuestion w = wrongMapper.selectOne(new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getUserId, uid).eq(WrongQuestion::getQuestionId, qid));
        if (w != null && w.getResolved() == 0) {
            w.setResolved(1);
            wrongMapper.updateById(w);
        }
    }

    /** PR-4 错题本 */
    public List<WrongQuestion> wrongBook(Long uid, Boolean resolved) {
        return wrongMapper.selectList(new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getUserId, uid)
                .eq(resolved != null, WrongQuestion::getResolved, resolved)
                .orderByDesc(WrongQuestion::getLastWrongAt));
    }

    /** PR-6 学情：按知识点聚合正确率（基于作答记录关联题目知识点，简化为错题聚合） */
    public List<Map<String, Object>> kpReport(Long uid) {
        Map<String, int[]> agg = new LinkedHashMap<>();
        for (WrongQuestion w : wrongMapper.selectList(new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getUserId, uid))) {
            for (String kp : (w.getKpNames() == null ? "未标注" : w.getKpNames()).split(",")) {
                int[] arr = agg.computeIfAbsent(kp.trim(), k -> new int[2]);
                arr[0]++;
                if (w.getResolved() == 1) arr[1]++;
            }
        }
        return agg.entrySet().stream()
                .map(e -> Map.<String, Object>of("kp", e.getKey(), "wrong", e.getValue()[0], "resolved", e.getValue()[1]))
                .collect(Collectors.toList());
    }
}
