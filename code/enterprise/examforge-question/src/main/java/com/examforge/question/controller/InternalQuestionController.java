package com.examforge.question.controller;

import com.examforge.question.domain.Question;
import com.examforge.question.service.QuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 题库内部接口（Feign：/internal/questions/**，仅内网可达，网关不路由该前缀）。
 * 返回裸对象（不经 Result 包装），便于 Feign 直接反序列化；供 paper 组卷与导出使用。
 */
@RestController
@RequestMapping("/internal/questions")
@RequiredArgsConstructor
public class InternalQuestionController {

    private final QuestionService service;

    @GetMapping("/stats")
    public java.util.Map<String, Object> stats() {
        return java.util.Map.of(
                "questions", service.countAll(),
                "onShelf", service.countOnShelf());
    }

    @GetMapping("/by-type")
    public List<Question> byType(@RequestParam Long subjectId, @RequestParam String type,
                                 @RequestParam(required = false) Integer difficulty,
                                 @RequestParam(defaultValue = "50") int limit) {
        return service.listByType(subjectId, type, difficulty, limit);
    }

    @GetMapping("/{id}")
    public Question byId(@PathVariable Long id) {
        return service.detail(id);
    }

    /** 按 ID 批量取题（paper 考查范围聚合/导出渲染，避免逐题 N+1 调用） */
    @PostMapping("/by-ids")
    public List<Question> byIds(@RequestBody List<Long> ids) {
        return service.listByIds(ids);
    }

    @GetMapping("/by-kp")
    public List<Question> byKp(@RequestParam Long subjectId, @RequestParam String kp,
                               @RequestParam(required = false) String type,
                               @RequestParam(defaultValue = "20") int limit) {
        return service.listByType(subjectId, type, null, limit).stream()
                .filter(q -> q.getKpNames() != null && q.getKpNames().contains(kp))
                .limit(limit)
                .toList();
    }

    /** AI 变式题草稿入库：status=1 审核中 + aigc=1，由编辑终审（docs/15 AI-2/AI-4） */
    @PostMapping("/create-draft")
    public Long createDraft(@RequestBody Question q) {
        q.setId(null);
        q.setStatus(1);
        if (q.getAigc() == null) q.setAigc(1);
        if (q.getUseCount() == null) q.setUseCount(0);
        q.setCreatedAt(java.time.LocalDateTime.now());
        service.save(q);
        return q.getId();
    }

    /** 检索（AI 搜后端通道，docs/16 S-2）：复用统一检索（ES 优先/降级 LIKE） */
    @GetMapping("/search")
    public List<Question> search(@RequestParam(required = false) String keyword,
                                 @RequestParam(required = false) Long subjectId,
                                 @RequestParam(required = false) String type,
                                 @RequestParam(required = false) Integer difficulty,
                                 @RequestParam(defaultValue = "10") int limit) {
        return service.search(subjectId, null, type, difficulty, null, null, keyword, null, 1, limit).getRecords();
    }
}
