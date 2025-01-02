package com.examforge.question.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.Result;
import com.examforge.question.domain.Question;
import com.examforge.question.mapper.QuestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 题目审核工作流（docs/02 4.4 / docs/14 三审制精简版）：
 * EDITOR/SUPER_ADMIN 对 status=1（审核中）题目终审：
 * 通过 → status=2 上架并署名 reviewer；驳回 → status=3 下架（可改后重提）。
 * 审核人/时间落库（解析生产署名链，docs/10 §2.2）。
 */
@RestController
@RequestMapping("/api/v1/questions/admin")
@RequiredArgsConstructor
public class QuestionAuditController {

    private final QuestionMapper mapper;
    private final com.examforge.question.service.EsSearchService esSearchService;
    private final com.examforge.question.service.QuestionService questionService;

    private void requireEditor(String role) {
        if (!"EDITOR".equals(role) && !"SUPER_ADMIN".equals(role)) {
            throw new BizException(Result.FORBIDDEN, "仅学科编辑或超级管理员可审核");
        }
    }

    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestHeader("X-User-Role") String role,
                                            @RequestParam(required = false) Integer status,
                                            @RequestParam(defaultValue = "1") long pageNo,
                                            @RequestParam(defaultValue = "20") long pageSize) {
        requireEditor(role);
        LambdaQueryWrapper<Question> w = new LambdaQueryWrapper<Question>()
                .orderByDesc(Question::getId)
                .eq(status != null, Question::getStatus, status);
        Page<Question> p = mapper.selectPage(new Page<>(pageNo, Math.min(pageSize, 50)), w);
        return Result.ok(Map.of("total", p.getTotal(), "list", p.getRecords()));
    }

    /** 终审：approve=true 上架 / false 驳回下架 */
    @PutMapping("/{id}/audit")
    public Result<Map<String, Object>> audit(@RequestHeader("X-User-Role") String role,
                                             @RequestHeader(value = "X-User-Nickname", required = false) String nickname,
                                             @PathVariable Long id,
                                             @RequestBody Map<String, Object> body) {
        requireEditor(role);
        Question q = mapper.selectById(id);
        if (q == null) return Result.fail(Result.NOT_FOUND, "题目不存在");
        boolean approve = Boolean.parseBoolean(String.valueOf(body.get("approve")));
        q.setStatus(approve ? 2 : 3);
        q.setReviewer((nickname == null || nickname.isBlank() ? "编辑#" + role : nickname) + " " + LocalDateTime.now().toLocalDate());
        mapper.updateById(q);
        if (approve) {   // 上架即入 ES 索引（未启用时为 no-op）
            esSearchService.index(q.getId(), q.getSubjectId(), q.getType(), q.getDifficulty(),
                    q.getScene(), q.getCategory(), q.getKpNames(), q.getStem(), q.getSource());
        }
        questionService.evictDetail(id);   // 审核变更剔除详情缓存
        return Result.ok(Map.of("id", id, "status", q.getStatus(), "reviewer", q.getReviewer()));
    }

    /** 全量重建索引（换 ES 集群/改 mapping 后执行；分批走库，量大时可改 XXL-Job 分片） */
    @PostMapping("/reindex")
    public Result<Map<String, Object>> reindex(@RequestHeader("X-User-Role") String role) {
        requireEditor(role);
        long total = mapper.selectCount(new LambdaQueryWrapper<Question>().eq(Question::getStatus, 2));
        long pageSize = 500;
        long indexed = 0;
        for (long page = 1; page <= (total + pageSize - 1) / pageSize; page++) {
            var rows = mapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize),
                    new LambdaQueryWrapper<Question>().eq(Question::getStatus, 2)).getRecords();
            for (Question q : rows) {
                esSearchService.index(q.getId(), q.getSubjectId(), q.getType(), q.getDifficulty(),
                        q.getScene(), q.getCategory(), q.getKpNames(), q.getStem(), q.getSource());
                indexed++;
            }
        }
        return Result.ok(Map.of("indexed", indexed));
    }

    /** 转审核：录题提交后 status 1（教师/编辑均可触发） */
    @PutMapping("/{id}/submit")
    public Result<Map<String, Object>> submit(@RequestHeader("X-User-Role") String role, @PathVariable Long id) {
        if (!"EDITOR".equals(role) && !"SUPER_ADMIN".equals(role) && !"TEACHER".equals(role)) {
            throw new BizException(Result.FORBIDDEN, "无权限");
        }
        Question q = mapper.selectById(id);
        if (q == null) return Result.fail(Result.NOT_FOUND, "题目不存在");
        q.setStatus(1);
        mapper.updateById(q);
        return Result.ok(Map.of("id", id, "status", 1));
    }
}
