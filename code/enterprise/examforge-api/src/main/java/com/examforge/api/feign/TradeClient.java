package com.examforge.api.feign;

import com.examforge.api.dto.BillingDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 交易服务 Feign 契约（paper 导出计费 + 资源下载扣点 + 创作者分成入账，docs/14 §6、docs/26 §6）。
 * 全路径声明：公开端点 /api/v1/**（TradeController），内部端点 /internal/trade/**（InternalTradeController）；
 * 不可用类级 path 混用两类前缀——path 会整体前缀化，曾致内部点数端点 404。
 */
@FeignClient(name = "examforge-trade")
public interface TradeClient {

    @GetMapping("/api/v1/trade/billing")
    BillingDTO billing(@RequestHeader("X-User-Id") String userId,
                       @RequestParam("questionCount") int questionCount,
                       @RequestParam("paperHash") String paperHash);

    @PostMapping("/api/v1/trade/consume")
    Map<String, Object> consume(@RequestHeader("X-User-Id") String userId,
                                @RequestBody Map<String, Object> body);

    /** 权益判定（会员是否有效/点数/免费额度），AI 配额治理使用（docs/15 AI-4） */
    @GetMapping("/api/v1/member/me")
    Map<String, Object> entitlement(@RequestHeader("X-User-Id") String userId);

    /** 纠错采纳/注册/邀请等奖励点数（feedback 与增长闭环，docs/10） */
    @PostMapping("/internal/trade/points/reward")
    Map<String, Object> reward(@RequestHeader("X-User-Id") String userId,
                               @RequestBody Map<String, Object> body);

    /** 资源下载等场景的点数扣减（resource 服务计费闭环，docs/26 F-XKW-02） */
    @PostMapping("/internal/trade/points/deduct")
    Map<String, Object> deduct(@RequestHeader("X-User-Id") String userId,
                               @RequestBody Map<String, Object> body);

    /** 创作者分成等场景的点数入账（resource 服务分成闭环，docs/26 §6） */
    @PostMapping("/internal/trade/points/credit")
    Map<String, Object> credit(@RequestHeader("X-User-Id") String userId,
                               @RequestBody Map<String, Object> body);
}
