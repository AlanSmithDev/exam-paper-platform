package com.examforge.trade.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.Result;
import com.examforge.trade.domain.CouponTemplate;
import com.examforge.trade.domain.MemberPlan;
import com.examforge.trade.domain.UserCoupon;
import com.examforge.trade.mapper.CouponTemplateMapper;
import com.examforge.trade.mapper.MemberPlanMapper;
import com.examforge.trade.mapper.UserCouponMapper;
import com.examforge.trade.service.TradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 交易域对外接口（网关路由 /api/v1/member|coupons|trade|payments/**） */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TradeController {

    private final TradeService tradeService;
    private final MemberPlanMapper planMapper;
    private final CouponTemplateMapper couponTemplateMapper;
    private final UserCouponMapper userCouponMapper;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @Value("${examforge.payment.provider:MOCK}")
    private String paymentProvider;

    @GetMapping("/member/plans")
    public Result<List<MemberPlan>> plans() {
        return Result.ok(planMapper.selectList(null));
    }

    @GetMapping("/member/me")
    public Result<Map<String, Object>> me(@RequestHeader("X-User-Id") String uid) {
        return Result.ok(tradeService.entitlement(Long.valueOf(uid)));
    }

    /** 下单（幂等：X-Idempotency-Key） */
    @PostMapping("/member/orders")
    public Result<Map<String, Object>> createOrder(@RequestHeader("X-User-Id") String uid,
                                                   @RequestHeader(value = "X-Idempotency-Key", required = false) String idem,
                                                   @RequestBody Map<String, Object> body) {
        if (idem == null || idem.isBlank()) throw new BizException(Result.BAD_REQUEST, "缺少 X-Idempotency-Key");
        return Result.ok(tradeService.createOrder(Long.valueOf(uid),
                String.valueOf(body.getOrDefault("skuType", "MEMBER")),
                String.valueOf(body.getOrDefault("skuRef", "TEACHER_PRO")),
                body.get("quantity") == null ? 1 : Integer.parseInt(String.valueOf(body.get("quantity"))),
                body.get("couponId") == null ? null : Long.valueOf(String.valueOf(body.get("couponId"))),
                idem));
    }

    /** 沙箱支付回调（生产替换为微信/支付宝验签回调，入口同样幂等；仅 MOCK 模式开放） */
    @PostMapping("/payments/mock-notify")
    public Result<Map<String, Object>> mockNotify(@RequestBody Map<String, String> body) {
        if (!"MOCK".equalsIgnoreCase(paymentProvider)) {
            throw new BizException(Result.FORBIDDEN, "当前支付渠道非 MOCK，禁止使用沙箱回调");
        }
        // 支付回调结果计数（G3 业务指标，docs/19 §5：支付成功率 <99.9% 即 P0）
        try {
            Map<String, Object> out = tradeService.payNotify(body.get("orderNo"));
            meterRegistry.counter("examforge_payment_callback_total", "result",
                    "PAID".equals(String.valueOf(out.get("status"))) ? "SUCCESS" : "IGNORED").increment();
            return Result.ok(out);
        } catch (BizException e) {
            meterRegistry.counter("examforge_payment_callback_total", "result", "FAIL").increment();
            throw e;
        }
    }

    @GetMapping("/coupons/available")
    public Result<List<CouponTemplate>> availableCoupons() {
        return Result.ok(couponTemplateMapper.selectList(new LambdaQueryWrapper<CouponTemplate>()
                .eq(CouponTemplate::getStatus, 1)));
    }

    @PostMapping("/coupons/{templateId}/claim")
    public Result<Map<String, Object>> claim(@RequestHeader("X-User-Id") String uid, @PathVariable Long templateId) {
        return Result.ok(Map.of("couponId", tradeService.claimCoupon(Long.valueOf(uid), templateId)));
    }

    @GetMapping("/coupons/mine")
    public Result<List<UserCoupon>> mine(@RequestHeader("X-User-Id") String uid) {
        return Result.ok(userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, Long.valueOf(uid)).orderByDesc(UserCoupon::getId)));
    }

    /** 签到（每日一次 +1 点，docs/14 K-3） */
    @PostMapping("/trade/checkin")
    public Result<Map<String, Object>> checkin(@RequestHeader("X-User-Id") String uid) {
        return Result.ok(tradeService.checkin(Long.valueOf(uid)));
    }

    /** 会员激活码兑换（docs/26 F-XKW-06）：支持会员天数/点数/优惠券三种奖励 */
    @PostMapping("/trade/cdk/redeem")
    public Result<Map<String, Object>> redeemCdk(@RequestHeader("X-User-Id") String uid,
                                                 @RequestBody Map<String, String> body) {
        return Result.ok(tradeService.redeemCdk(Long.valueOf(uid), body.get("code")));
    }

    /** 积分任务中心（docs/26 F-XKW-07）：任务列表 + 完成状态 */
    @GetMapping("/trade/tasks")
    public Result<List<Map<String, Object>>> tasks(@RequestHeader("X-User-Id") String uid) {
        return Result.ok(tradeService.listTasks(Long.valueOf(uid)));
    }

    /** 主动完成任务（自动任务由各服务经 /internal/trade/task/complete 上报） */
    @PostMapping("/trade/tasks/{taskKey}/complete")
    public Result<Map<String, Object>> completeTask(@RequestHeader("X-User-Id") String uid,
                                                    @PathVariable String taskKey) {
        return Result.ok(tradeService.completeTask(Long.valueOf(uid), taskKey));
    }

    /** 下载判价（examforge-paper 导出前调用） */
    @GetMapping("/trade/billing")
    public Result<Map<String, Object>> billing(@RequestHeader("X-User-Id") String uid,
                                               @RequestParam int questionCount, @RequestParam String paperHash) {
        return Result.ok(tradeService.billing(Long.valueOf(uid), questionCount, paperHash));
    }

    /** 导出成功后扣费 */
    @PostMapping("/trade/consume")
    public Result<Map<String, Object>> consume(@RequestHeader("X-User-Id") String uid,
                                               @RequestBody Map<String, Object> body) {
        return Result.ok(tradeService.consumeDownload(Long.valueOf(uid),
                Integer.parseInt(String.valueOf(body.get("questionCount"))),
                String.valueOf(body.get("paperHash")),
                String.valueOf(body.getOrDefault("mode", "FREE"))));
    }
}
