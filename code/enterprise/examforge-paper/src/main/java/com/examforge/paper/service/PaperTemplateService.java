package com.examforge.paper.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.Result;
import com.examforge.paper.domain.Paper;
import com.examforge.paper.domain.PaperQuestion;
import com.examforge.paper.domain.PaperTemplate;
import com.examforge.paper.logic.TemplateRules;
import com.examforge.paper.mapper.PaperMapper;
import com.examforge.paper.mapper.PaperQuestionMapper;
import com.examforge.paper.mapper.PaperTemplateMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 试卷模板服务（docs/25 TJ-80：存为模板 / 模板选题 / 删除；题目快照口径） */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperTemplateService {

    private final PaperTemplateMapper templateMapper;
    private final PaperMapper paperMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final ObjectMapper objectMapper;

    /** 存为模板：从现有卷快照题目与分值（卷面顺序） */
    public Map<String, Object> saveFromPaper(Long uid, Long paperId, String name) {
        Paper paper = paperMapper.selectById(paperId);
        if (paper == null || !paper.getUserId().equals(uid)) {
            throw new BizException(Result.NOT_FOUND, "试卷不存在");
        }
        List<PaperQuestion> rows = paperQuestionMapper.selectList(new LambdaQueryWrapper<PaperQuestion>()
                .eq(PaperQuestion::getPaperId, paperId).orderByAsc(PaperQuestion::getSort));
        if (rows.isEmpty()) throw new BizException(Result.BAD_REQUEST, "试卷为空，无法存为模板");

        List<TemplateRules.SnapshotItem> snapshot = rows.stream()
                .map(r -> new TemplateRules.SnapshotItem(r.getQuestionId(), r.getScore())).toList();
        String invalid = TemplateRules.validateSnapshot(snapshot);
        if (invalid != null) throw new BizException(Result.BAD_REQUEST, invalid);

        long count = templateMapper.selectCount(new LambdaQueryWrapper<PaperTemplate>()
                .eq(PaperTemplate::getUserId, uid));
        if (!TemplateRules.canCreate(count)) {
            throw new BizException(Result.BAD_REQUEST, "模板数量已达上限（" + TemplateRules.MAX_TEMPLATES_PER_USER + "），请先删除部分模板");
        }

        PaperTemplate t = new PaperTemplate();
        t.setUserId(uid);
        t.setName(TemplateRules.normalizeName(name));
        t.setSourcePaperId(paperId);
        t.setQuestions(toJson(snapshot));
        t.setTotalScore(TemplateRules.totalScore(snapshot));
        t.setCreatedAt(LocalDateTime.now());
        templateMapper.insert(t);
        return Map.of("templateId", t.getId(), "name", t.getName(), "count", snapshot.size(),
                "totalScore", t.getTotalScore());
    }

    /** 我的模板列表 */
    public List<Map<String, Object>> mine(Long uid) {
        return templateMapper.selectList(new LambdaQueryWrapper<PaperTemplate>()
                        .eq(PaperTemplate::getUserId, uid).orderByDesc(PaperTemplate::getId)).stream()
                .map(t -> Map.<String, Object>of(
                        "id", t.getId(), "name", t.getName(),
                        "totalScore", t.getTotalScore() == null ? 0 : t.getTotalScore(),
                        "createdAt", t.getCreatedAt() == null ? "" : t.getCreatedAt().toString()))
                .toList();
    }

    /** 模板选题：从模板快照复制生成新卷（编辑中，含题目与分值） */
    @Transactional
    public Map<String, Object> apply(Long uid, Long templateId, String title) {
        PaperTemplate t = templateMapper.selectById(templateId);
        if (t == null || !t.getUserId().equals(uid)) throw new BizException(Result.NOT_FOUND, "模板不存在");
        List<TemplateRules.SnapshotItem> snapshot = fromJson(t.getQuestions());
        String invalid = TemplateRules.validateSnapshot(snapshot);
        if (invalid != null) throw new BizException(Result.BAD_REQUEST, "模板快照已失效：" + invalid);

        Paper paper = new Paper();
        paper.setUserId(uid);
        paper.setTitle(title == null || title.isBlank()
                ? TemplateRules.appliedTitle(t.getName(), LocalDateTime.now()) : title.trim());
        paper.setTotalScore(TemplateRules.totalScore(snapshot));
        paper.setStatus(0);
        paper.setCreatedAt(LocalDateTime.now());
        paperMapper.insert(paper);

        int sort = 0;
        for (TemplateRules.SnapshotItem item : snapshot) {
            PaperQuestion pq = new PaperQuestion();
            pq.setPaperId(paper.getId());
            pq.setQuestionId(item.questionId());
            pq.setScore(item.score());
            pq.setSort(++sort);
            paperQuestionMapper.insert(pq);
        }
        log.info("模板应用出卷: template={} user={} → paper={} ({}题)", templateId, uid, paper.getId(), snapshot.size());
        return Map.of("paperId", paper.getId(), "title", paper.getTitle(),
                "count", snapshot.size(), "totalScore", paper.getTotalScore());
    }

    public Map<String, Object> delete(Long uid, Long templateId) {
        PaperTemplate t = templateMapper.selectById(templateId);
        if (t == null || !t.getUserId().equals(uid)) throw new BizException(Result.NOT_FOUND, "模板不存在");
        templateMapper.deleteById(templateId);
        return Map.of("deleted", 1);
    }

    // ---------- 快照序列化 ----------

    private String toJson(List<TemplateRules.SnapshotItem> items) {
        try {
            List<Map<String, Object>> list = new ArrayList<>();
            for (TemplateRules.SnapshotItem i : items) list.add(Map.of("questionId", i.questionId(), "score", i.score()));
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            throw new BizException(Result.SYSTEM, "模板快照序列化失败");
        }
    }

    private List<TemplateRules.SnapshotItem> fromJson(String json) {
        try {
            List<TemplateRules.SnapshotItem> items = new ArrayList<>();
            for (var node : objectMapper.readTree(json)) {
                items.add(new TemplateRules.SnapshotItem(node.get("questionId").asLong(), node.get("score").asInt()));
            }
            return items;
        } catch (Exception e) {
            throw new BizException(Result.BAD_REQUEST, "模板快照解析失败");
        }
    }
}
