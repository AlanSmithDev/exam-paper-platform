package com.examforge.paper.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.examforge.api.feign.QuestionClient;
import com.examforge.common.web.Result;
import com.examforge.paper.domain.Paper;
import com.examforge.paper.domain.PaperQuestion;
import com.examforge.paper.mapper.PaperMapper;
import com.examforge.paper.mapper.PaperQuestionMapper;
import com.examforge.paper.service.PaperGenerateEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 智能组卷 + 试卷管理 */
@RestController
@RequestMapping("/api/v1/papers")
public class PaperController {

    private final PaperGenerateEngine engine;
    private final PaperMapper paperMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final com.examforge.paper.service.PaperEditService editService;
    private final com.examforge.paper.service.PaperTemplateService templateService;

    public PaperController(QuestionClient questionClient, PaperMapper paperMapper,
                           PaperQuestionMapper paperQuestionMapper,
                           com.examforge.paper.service.PaperEditService editService,
                           com.examforge.paper.service.PaperTemplateService templateService) {
        // 组卷引擎的取题通道 = Feign 契约（跨服务调用题库服务）
        this.engine = new PaperGenerateEngine(questionClient::listByType);
        this.paperMapper = paperMapper;
        this.paperQuestionMapper = paperQuestionMapper;
        this.editService = editService;
        this.templateService = templateService;
    }

    @PostMapping("/generate")
    public Result<Map<String, Object>> generate(@RequestHeader("X-User-Id") String uid,
                                                @RequestBody Map<String, Object> body) {
        Long subjectId = body.get("subjectId") == null ? null : Long.valueOf(String.valueOf(body.get("subjectId")));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> structure = (List<Map<String, Object>>) body.getOrDefault("structure", List.of());
        Integer target = body.get("difficultyTarget") == null ? null : Integer.valueOf(String.valueOf(body.get("difficultyTarget")));
        @SuppressWarnings("unchecked")
        List<Long> exclude = body.get("excludeIds") == null ? List.of()
                : ((List<Object>) body.get("excludeIds")).stream().map(x -> Long.valueOf(String.valueOf(x))).toList();

        // 引擎通过 Feign 拉取候选题
        PaperGenerateEngine.Outcome out = engine.generate(subjectId, structure, target, exclude);
        List<Map<String, Object>> questions = out.questions().stream()
                .map(p -> Map.<String, Object>of("id", p.id(), "type", p.type(), "difficulty", p.difficulty(),
                        "coefficient", p.coefficient(), "stem", p.stem(), "score", p.score()))
                .toList();

        Paper paper = new Paper();
        paper.setUserId(Long.valueOf(uid));
        paper.setTitle(String.valueOf(body.getOrDefault("title", "智能组卷")));
        paper.setTotalScore(out.totalScore());
        paper.setStatus(0);
        paperMapper.insert(paper);

        // 保存卷面结构（导出/二次编辑依据）
        List<com.examforge.paper.domain.PaperQuestion> rows = out.questions().stream()
                .map(p -> { com.examforge.paper.domain.PaperQuestion pq = new com.examforge.paper.domain.PaperQuestion();
                    pq.setPaperId(paper.getId()); pq.setQuestionId(p.id()); pq.setScore(p.score()); return pq; })
                .toList();
        for (int i = 0; i < rows.size(); i++) { rows.get(i).setSort(i + 1); paperQuestionMapper.insert(rows.get(i)); }

        return Result.ok(Map.of(
                "paperId", paper.getId(), "totalScore", out.totalScore(),
                "count", out.questions().size(), "difficultyFit", out.difficultyFit(),
                "warnings", out.warnings(), "questions", questions));
    }

    @GetMapping("/{id}")
    public Result<Paper> get(@RequestHeader("X-User-Id") String uid, @PathVariable Long id) {
        Paper p = paperMapper.selectById(id);
        if (p == null || !p.getUserId().equals(Long.valueOf(uid))) return Result.fail(Result.NOT_FOUND, "试卷不存在");
        return Result.ok(p);
    }

    /** 我的云端试卷列表（工作台"云端同步"与个人空间使用） */
    @GetMapping("/mine")
    public Result<List<Paper>> mine(@RequestHeader("X-User-Id") String uid) {
        return Result.ok(paperMapper.selectList(new LambdaQueryWrapper<Paper>()
                .eq(Paper::getUserId, Long.valueOf(uid))
                .orderByDesc(Paper::getId)));
    }

    // ================= 组卷工作台编辑（docs/26 F-XKW-05） =================

    /** 整卷插题：{questionId, position?(1起始,缺省追加), score?(缺省按题型)} */
    @PostMapping("/{id}/questions")
    public Result<Map<String, Object>> insert(@RequestHeader("X-User-Id") String uid, @PathVariable Long id,
                                              @RequestBody Map<String, Object> body) {
        return Result.ok(editService.insertQuestion(Long.valueOf(uid), id,
                Long.valueOf(String.valueOf(body.get("questionId"))),
                body.get("position") == null ? null : Integer.valueOf(String.valueOf(body.get("position"))),
                body.get("score") == null ? null : Integer.valueOf(String.valueOf(body.get("score")))));
    }

    /** 整卷移除：后续题自动前移，总分重算 */
    @DeleteMapping("/{id}/questions/{questionId}")
    public Result<Map<String, Object>> remove(@RequestHeader("X-User-Id") String uid,
                                              @PathVariable Long id, @PathVariable Long questionId) {
        return Result.ok(editService.removeQuestion(Long.valueOf(uid), id, questionId));
    }

    /** 考查范围条：知识点按出现题数降序（对标学科网试卷详情"考查范围"） */
    @GetMapping("/{id}/scope")
    public Result<Map<String, Object>> scope(@RequestHeader("X-User-Id") String uid, @PathVariable Long id) {
        return Result.ok(editService.scope(Long.valueOf(uid), id));
    }

    // ================= 试卷模板（docs/25 TJ-80/TJ-22：存为模版 / 模板选题 / 删除） =================

    /** 存为模板：从现有卷快照题目与分值，{name} */
    @PostMapping("/{id}/save-template")
    public Result<Map<String, Object>> saveTemplate(@RequestHeader("X-User-Id") String uid, @PathVariable Long id,
                                                    @RequestBody Map<String, String> body) {
        return Result.ok(templateService.saveFromPaper(Long.valueOf(uid), id, body.get("name")));
    }

    /** 我的模板列表 */
    @GetMapping("/templates")
    public Result<List<Map<String, Object>>> templates(@RequestHeader("X-User-Id") String uid) {
        return Result.ok(templateService.mine(Long.valueOf(uid)));
    }

    /** 模板选题：快照复制生成新卷，{title?}（缺省=模板名·副本+日期） */
    @PostMapping("/templates/{id}/apply")
    public Result<Map<String, Object>> applyTemplate(@RequestHeader("X-User-Id") String uid, @PathVariable Long id,
                                                     @RequestBody(required = false) Map<String, String> body) {
        return Result.ok(templateService.apply(Long.valueOf(uid), id, body == null ? null : body.get("title")));
    }

    /** 删除模板（本人） */
    @DeleteMapping("/templates/{id}")
    public Result<Map<String, Object>> deleteTemplate(@RequestHeader("X-User-Id") String uid, @PathVariable Long id) {
        return Result.ok(templateService.delete(Long.valueOf(uid), id));
    }
}
