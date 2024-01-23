package com.examforge.question.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.examforge.api.feign.TradeClient;
import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.Result;
import com.examforge.question.domain.Feedback;
import com.examforge.question.mapper.FeedbackMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 纠错工单（docs/10）：用户提交 → 编辑处理（采纳奖励点数，通过 TradeClient 发放）→ 闭环。
 * 奖励标准：解析知识性/细节错误采纳 +5 点，其余 +2 点。
 */
@RestController
@RequestMapping("/api/v1/questions")
@RequiredArgsConstructor
public class FeedbackController {

    private static final List<String> TARGET_TYPES = List.of(
            "题干错误", "属性错误", "解析知识性错误", "解析细节错误", "其他错误");

    private final FeedbackMapper feedbackMapper;
    private final TradeClient tradeClient;

    /** 用户提交纠错（登录即可） */
    @PostMapping("/{id}/feedback")
    public Result<Map<String, Object>> submit(@RequestHeader("X-User-Id") String uid,
                                              @PathVariable Long id,
                                              @RequestBody Feedback body) {
        if (body.getTargetType() == null || !TARGET_TYPES.contains(body.getTargetType())) {
            return Result.fail(Result.BAD_REQUEST, "错误类型非法：" + TARGET_TYPES);
        }
        if (body.getDescription() == null || body.getDescription().isBlank()) {
            return Result.fail(Result.BAD_REQUEST, "错误描述必填");
        }
        body.setId(null);
        body.setQuestionId(id);
        body.setUserId(Long.valueOf(uid));
        body.setStatus(0);
        body.setCreatedAt(LocalDateTime.now());
        feedbackMapper.insert(body);
        return Result.ok(Map.of("ticketId", body.getId(),
                "msg", "提交成功，编辑采纳后将获得点数奖励"));
    }

    /** 编辑处理队列（EDITOR/SUPER_ADMIN） */
    @GetMapping("/admin/feedback")
    public Result<Map<String, Object>> list(@RequestHeader("X-User-Role") String role,
                                            @RequestParam(required = false) Integer status,
                                            @RequestParam(defaultValue = "1") long pageNo,
                                            @RequestParam(defaultValue = "20") long pageSize) {
        requireEditor(role);
        Page<Feedback> p = feedbackMapper.selectPage(new Page<>(pageNo, Math.min(pageSize, 50)),
                new LambdaQueryWrapper<Feedback>()
                        .eq(status != null, Feedback::getStatus, status)
                        .orderByAsc(Feedback::getStatus).orderByDesc(Feedback::getId));
        return Result.ok(Map.of("total", p.getTotal(), "list", p.getRecords()));
    }

    /** 处理工单：采纳 → 状态1 + 发放点数奖励（跨服务 Feign）；驳回 → 状态2 */
    @PutMapping("/admin/feedback/{id}/resolve")
    public Result<Map<String, Object>> resolve(@RequestHeader("X-User-Role") String role,
                                               @RequestHeader(value = "X-User-Nickname", required = false) String nickname,
                                               @PathVariable Long id,
                                               @RequestBody Map<String, Object> body) {
        requireEditor(role);
        Feedback f = feedbackMapper.selectById(id);
        if (f == null) return Result.fail(Result.NOT_FOUND, "工单不存在");
        if (f.getStatus() != 0) return Result.fail(Result.BAD_REQUEST, "工单已处理");

        boolean adopt = Boolean.parseBoolean(String.valueOf(body.get("adopt")));
        f.setStatus(adopt ? 1 : 2);
        f.setHandler(nickname == null || nickname.isBlank() ? role : nickname);
        f.setResolveRemark(String.valueOf(body.getOrDefault("remark", "")));
        f.setResolvedAt(LocalDateTime.now());
        if (adopt) {
            int reward = "解析知识性错误".equals(f.getTargetType())
                    || "解析细节错误".equals(f.getTargetType()) ? 5 : 2;
            f.setRewardPoints(reward);
            tradeClient.reward(String.valueOf(f.getUserId()),
                    Map.of("points", reward, "reason", "FEEDBACK_ADOPT", "ref", "FB" + id));
        }
        feedbackMapper.updateById(f);
        return Result.ok(Map.of("id", id, "status", f.getStatus(), "rewardPoints", f.getRewardPoints() == null ? 0 : f.getRewardPoints()));
    }

    private void requireEditor(String role) {
        if (!"EDITOR".equals(role) && !"SUPER_ADMIN".equals(role)) {
            throw new BizException(Result.FORBIDDEN, "仅学科编辑或超级管理员可处理工单");
        }
    }
}
