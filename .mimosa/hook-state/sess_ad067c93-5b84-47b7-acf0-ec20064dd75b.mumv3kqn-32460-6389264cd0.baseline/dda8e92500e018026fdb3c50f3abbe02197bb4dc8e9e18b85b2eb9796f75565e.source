package com.examforge.paper.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.examforge.api.dto.QuestionSummaryDTO;
import com.examforge.api.feign.QuestionClient;
import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.Result;
import com.examforge.paper.domain.Paper;
import com.examforge.paper.domain.PaperQuestion;
import com.examforge.paper.logic.PaperRules;
import com.examforge.paper.mapper.PaperMapper;
import com.examforge.paper.mapper.PaperQuestionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 组卷工作台编辑：整卷插题/移除重排/考查范围聚合（docs/26 F-XKW-05） */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperEditService {

    private final PaperMapper paperMapper;
    private final PaperQuestionMapper pqMapper;
    private final QuestionClient questionClient;

    /** 整卷插题：position 1 起始（1=插最前，缺省=追加）；分值缺省按题型，蓝图分值可覆盖 */
    @Transactional
    public Map<String, Object> insertQuestion(Long uid, Long paperId, Long questionId,
                                              Integer position, Integer score) {
        Paper p = ownedEditablePaper(uid, paperId);
        List<PaperQuestion> rows = loadRows(paperId);
        List<Long> orderedIds = rows.stream().map(PaperQuestion::getQuestionId).toList();
        if (PaperRules.duplicated(orderedIds, questionId)) {
            throw new BizException(Result.BAD_REQUEST, "该题已在本卷中");
        }
        QuestionSummaryDTO q = questionClient.getById(questionId);
        if (q == null || q.getId() == null) throw new BizException(Result.NOT_FOUND, "题目不存在或未上架");

        int pos = position == null ? orderedIds.size() + 1 : position;
        List<Long> after = apply(() -> PaperRules.applyInsert(orderedIds, questionId, pos));
        int scoreVal = score != null && score > 0 ? score : PaperRules.defaultScore(q.getType());

        applyOrder(paperId, rows, after);
        PaperQuestion row = new PaperQuestion();
        row.setPaperId(paperId);
        row.setQuestionId(questionId);
        row.setSort(after.indexOf(questionId) + 1);
        row.setScore(scoreVal);
        pqMapper.insert(row);
        int total = recalcTotal(paperId);
        log.info("整卷插题: paper={} question={} position={} score={}", paperId, questionId, row.getSort(), scoreVal);
        return Map.of("ok", true, "position", row.getSort(), "score", scoreVal,
                "count", after.size(), "totalScore", total);
    }

    /** 整卷移除：删除行 + 后续题前移 + 总分重算 */
    @Transactional
    public Map<String, Object> removeQuestion(Long uid, Long paperId, Long questionId) {
        ownedEditablePaper(uid, paperId);
        List<PaperQuestion> rows = loadRows(paperId);
        List<Long> orderedIds = rows.stream().map(PaperQuestion::getQuestionId).toList();
        if (!orderedIds.contains(questionId)) throw new BizException(Result.BAD_REQUEST, "该题不在本卷中");
        List<Long> after = apply(() -> PaperRules.applyRemove(orderedIds, questionId));

        pqMapper.delete(new LambdaQueryWrapper<PaperQuestion>()
                .eq(PaperQuestion::getPaperId, paperId).eq(PaperQuestion::getQuestionId, questionId));
        applyOrder(paperId, rows, after);
        int total = recalcTotal(paperId);
        return Map.of("ok", true, "count", after.size(), "totalScore", total);
    }

    /** 考查范围条（学科网试卷详情同构）：知识点按出现题数降序 */
    public Map<String, Object> scope(Long uid, Long paperId) {
        ownedPaper(uid, paperId);
        List<Long> ids = loadRows(paperId).stream().map(PaperQuestion::getQuestionId).toList();
        if (ids.isEmpty()) return Map.of("scope", List.of(), "distinct", 0);
        List<String> kpCsv = questionClient.listByIds(ids).stream()
                .map(QuestionSummaryDTO::getKpNames)
                .toList();
        List<Map<String, Object>> scope = PaperRules.scopeAggregation(kpCsv);
        return Map.of("scope", scope, "distinct", scope.size());
    }

    // ---------- 内部 ----------

    private Paper ownedPaper(Long uid, Long paperId) {
        Paper p = paperMapper.selectById(paperId);
        if (p == null || !p.getUserId().equals(uid)) throw new BizException(Result.NOT_FOUND, "试卷不存在");
        return p;
    }

    private Paper ownedEditablePaper(Long uid, Long paperId) {
        Paper p = ownedPaper(uid, paperId);
        if (p.getStatus() != null && p.getStatus() != 0) {
            throw new BizException(Result.BAD_REQUEST, "试卷已定稿，不能编辑");
        }
        return p;
    }

    private List<PaperQuestion> loadRows(Long paperId) {
        return pqMapper.selectList(new LambdaQueryWrapper<PaperQuestion>()
                .eq(PaperQuestion::getPaperId, paperId).orderByAsc(PaperQuestion::getSort));
    }

    /** 把 IllegalArgumentException 统一转为 400 业务异常 */
    private List<Long> apply(java.util.function.Supplier<List<Long>> fn) {
        try {
            return fn.get();
        } catch (IllegalArgumentException e) {
            throw new BizException(Result.BAD_REQUEST, e.getMessage());
        }
    }

    /** 按新顺序回写 sort（仅更新变化行，行数 ≤100，同事务内完成） */
    private void applyOrder(Long paperId, List<PaperQuestion> rows, List<Long> newOrder) {
        Map<Long, PaperQuestion> byQid = new HashMap<>();
        for (PaperQuestion r : rows) byQid.putIfAbsent(r.getQuestionId(), r);
        for (int i = 0; i < newOrder.size(); i++) {
            PaperQuestion r = byQid.get(newOrder.get(i));
            if (r != null && (r.getSort() == null || r.getSort() != i + 1)) {
                PaperQuestion upd = new PaperQuestion();
                upd.setId(r.getId());
                upd.setSort(i + 1);
                pqMapper.updateById(upd);
            }
        }
    }

    private int recalcTotal(Long paperId) {
        int total = loadRows(paperId).stream()
                .mapToInt(r -> r.getScore() == null ? 0 : r.getScore()).sum();
        Paper upd = new Paper();
        upd.setId(paperId);
        upd.setTotalScore(total);
        paperMapper.updateById(upd);
        return total;
    }
}
