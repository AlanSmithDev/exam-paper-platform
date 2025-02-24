package com.examforge.admin.controller;

import com.examforge.api.feign.PaperStatsClient;
import com.examforge.api.feign.QuestionStatsClient;
import com.examforge.api.feign.TradeStatsClient;
import com.examforge.api.feign.UserStatsClient;
import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据看板（docs/12 §2.7 / docs/16 §2）：admin 服务经 Feign 聚合各服务自报统计。
 * 任一服务统计不可用时降级显示 "—"，不影响其余指标。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class DashboardController {

    private final UserStatsClient userStats;
    private final QuestionStatsClient questionStats;
    private final PaperStatsClient paperStats;
    private final TradeStatsClient tradeStats;

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("users", num(safe(() -> userStats.stats(), "users")));
        out.put("questions", num(safe(() -> questionStats.stats(), "questions")));
        out.put("questionsOnShelf", num(safe(() -> questionStats.stats(), "onShelf")));
        out.put("papers", num(safe(() -> paperStats.stats(), "papers")));
        out.put("orders", num(safe(() -> tradeStats.stats(), "orders")));
        out.put("paidOrders", num(safe(() -> tradeStats.stats(), "paidOrders")));
        return Result.ok(out);
    }

    private Object safe(java.util.function.Supplier<Map<String, Object>> call, String key) {
        try {
            Map<String, Object> m = call.get();
            return m == null ? null : m.get(key);
        } catch (Exception e) {
            log.warn("看板统计拉取失败 {}: {}", key, e.getMessage());
            return "—";
        }
    }

    private Object num(Object v) { return v == null ? "—" : v; }
}
