package com.examforge.trade.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.examforge.common.web.Result;
import com.examforge.trade.domain.TradeOrder;
import com.examforge.trade.logic.OrderRules;
import com.examforge.trade.mapper.TradeOrderMapper;
import com.examforge.trade.service.TradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 服务间内部接口（仅内网可达，网关不路由 /internal/**；docs/12 SSRF 与安全约束同样适用） */
@RestController
@RequestMapping("/internal/trade")
@RequiredArgsConstructor
public class InternalTradeController {

    private final TradeService tradeService;
    private final TradeOrderMapper orderMapper;

    /** 纠错采纳等奖励发放：question 服务 Feign 调用 */
    @PostMapping("/points/reward")
    public Result<Map<String, Object>> reward(@RequestHeader("X-User-Id") String uid,
                                              @RequestBody Map<String, Object> body) {
        int points = Integer.parseInt(String.valueOf(body.getOrDefault("points", "0")));
        if (points <= 0 || points > 100) return Result.fail(Result.BAD_REQUEST, "奖励点数非法");
        return Result.ok(tradeService.reward(Long.valueOf(uid), points,
                String.valueOf(body.getOrDefault("reason", "REWARD")),
                String.valueOf(body.getOrDefault("ref", ""))));
    }

    /** 统计（看板聚合） */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.ok(Map.of(
                "orders", orderMapper.selectCount(null),
                "paidOrders", orderMapper.selectCount(new LambdaQueryWrapper<TradeOrder>()
                        .eq(TradeOrder::getStatus, OrderRules.PAID))));
    }

    /** 任务完成上报（paper/question/ai 服务 Feign 调用，如 FIRST_COMPOSE/FIRST_UPLOAD/AI_FIRST_USE） */
    @PostMapping("/task/complete")
    public Result<Map<String, Object>> completeTask(@RequestHeader("X-User-Id") String uid,
                                                    @RequestBody Map<String, Object> body) {
        return Result.ok(tradeService.completeTask(Long.valueOf(uid),
                String.valueOf(body.getOrDefault("taskKey", ""))));
    }

    /** 点数扣减（resource 下载计费等场景 Feign 调用，条件更新防透支） */
    @PostMapping("/points/deduct")
    public Result<Map<String, Object>> deduct(@RequestHeader("X-User-Id") String uid,
                                              @RequestBody Map<String, Object> body) {
        int points = Integer.parseInt(String.valueOf(body.getOrDefault("points", "0")));
        return Result.ok(tradeService.deduct(Long.valueOf(uid), points,
                String.valueOf(body.getOrDefault("reason", "DEDUCT")),
                String.valueOf(body.getOrDefault("ref", ""))));
    }

    /** 点数入账（创作者分成等场景 resource 服务 Feign 调用，docs/26 §6；与 reward 区分理由码，上限 10 万点） */
    @PostMapping("/points/credit")
    public Result<Map<String, Object>> credit(@RequestHeader("X-User-Id") String uid,
                                              @RequestBody Map<String, Object> body) {
        int points = Integer.parseInt(String.valueOf(body.getOrDefault("points", "0")));
        if (points <= 0 || points > 100000) return Result.fail(Result.BAD_REQUEST, "入账点数非法");
        return Result.ok(tradeService.credit(Long.valueOf(uid), points,
                String.valueOf(body.getOrDefault("reason", "CREDIT")),
                String.valueOf(body.getOrDefault("ref", ""))));
    }
}
