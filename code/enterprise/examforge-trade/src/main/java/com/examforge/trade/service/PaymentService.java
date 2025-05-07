package com.examforge.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.Result;
import com.examforge.trade.domain.PaymentCallback;
import com.examforge.trade.domain.TradeOrder;
import com.examforge.trade.logic.PaymentRules;
import com.examforge.trade.mapper.PaymentCallbackMapper;
import com.examforge.trade.mapper.TradeOrderMapper;
import com.examforge.trade.payment.PaymentProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 支付渠道编排（docs/14 O-4/D14）：渠道下单 / 回调落账（R7 防重放）与幂等支付 / 主动查单兜底。
 * 支付成功统一走 TradeService.payNotify（已含三板斧①③），本服务补齐第②板：provider+callback_id 唯一防重放。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final TradeOrderMapper orderMapper;
    private final PaymentCallbackMapper callbackMapper;
    private final TradeService tradeService;
    private final List<PaymentProvider> providers;

    private PaymentProvider provider() {
        // 条件装配下容器内只会有当前渠道一个实现；数量>1 视为装配异常
        if (providers.size() != 1) {
            throw new BizException(Result.SYSTEM, "支付渠道装配异常（" + providers.size() + " 个实现）");
        }
        return providers.get(0);
    }

    /** 渠道下单（登录+本人+CREATED）→ 支付凭证 */
    public Map<String, Object> prepay(Long uid, String orderNo, String returnUrl) {
        TradeOrder o = ownOrder(uid, orderNo);
        if (!"CREATED".equals(o.getStatus())) {
            throw new BizException(Result.BAD_REQUEST, "订单状态为 " + o.getStatus() + "，无需支付");
        }
        PaymentProvider p = provider();
        PaymentProvider.PrepayResult r = p.prepay(o, returnUrl);
        log.info("渠道下单: provider={} order={} mode={}", p.name(), orderNo, r.mode());
        return Map.of("provider", p.name(), "mode", r.mode(),
                "codeUrl", r.codeUrl() == null ? "" : r.codeUrl(),
                "payUrl", r.payUrl() == null ? "" : r.payUrl(),
                "hint", r.raw() == null ? "" : r.raw());
    }

    /**
     * 渠道回调统一入口（R7）：验签（provider 内）→ 落回调流水（唯一索引防重放）→ 金额比对防篡改 → 幂等支付。
     * 验签失败由 provider 抛出（渠道会重试）；重复 callback_id 直接幂等返回。
     */
    public Map<String, Object> handleCallback(String providerName, Map<String, String> headers,
                                              String body, Map<String, String> form) {
        PaymentProvider p = byName(providerName);
        PaymentProvider.CallbackResult cb = p.verifyCallback(headers, body, form);

        TradeOrder o = orderMapper.selectOne(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getOrderNo, cb.orderNo()));
        if (o == null) {
            log.warn("回调订单不存在: provider={} orderNo={}", p.name(), cb.orderNo());
            throw new BizException(Result.NOT_FOUND, "订单不存在");
        }

        // R7-② 防重放：provider+callback_id 唯一，重复回调直接幂等返回（不重复履约）
        PaymentCallback record = new PaymentCallback();
        record.setOrderNo(cb.orderNo());
        record.setProvider(p.name());
        record.setCallbackId(cb.callbackId());
        record.setPaid(cb.paid() ? 1 : 0);
        record.setPayload(cb.raw() == null || cb.raw().length() <= 1900 ? cb.raw() : cb.raw().substring(0, 1900));
        record.setCreatedAt(LocalDateTime.now());
        boolean replayed = false;
        try {
            callbackMapper.insert(record);
        } catch (DuplicateKeyException e) {
            replayed = true;
        }
        if (replayed) {
            log.info("回调重放拦截（幂等）: provider={} callbackId={}", p.name(), cb.callbackId());
            return Map.of("orderNo", cb.orderNo(), "status", "PAID", "idempotent", true);
        }

        // R7 金额防篡改：渠道回传金额必须与订单应付一致——不符则把流水标记为未采信（paid=0）并拒绝（人工跟进）
        if (cb.paid() && !PaymentRules.amountMatches(o.getPayCents(), cb.amountCents() == null ? -1 : cb.amountCents())) {
            record.setPaid(0);
            callbackMapper.updateById(record);
            log.error("回调金额与订单不符（疑似篡改）: provider={} order={} 回调={} 应付={}",
                    p.name(), cb.orderNo(), cb.amountCents(), o.getPayCents());
            throw new BizException(Result.BAD_REQUEST, "回调金额与订单不符");
        }

        if (!cb.paid()) {
            return Map.of("orderNo", cb.orderNo(), "status", o.getStatus(), "idempotent", false, "paid", false);
        }
        return tradeService.payNotify(cb.orderNo());   // 三板斧①③在 payNotify 内
    }

    /** 支付状态查询（登录+本人）：本地状态 + 渠道查单兜底（CREATED 且非 MOCK 时主动查，对账精神 docs/19 §4） */
    public Map<String, Object> status(Long uid, String orderNo) {
        TradeOrder o = ownOrder(uid, orderNo);
        PaymentProvider p = provider();
        String channelState = "UNKNOWN";
        if ("CREATED".equals(o.getStatus()) && !PaymentRules.MOCK.equals(p.name())) {
            channelState = p.queryOrder(o);
            if ("PAID".equals(channelState)) {
                // 渠道已支付但本地未落账（回调丢失）：以查单结果构造幂等回调补履约（docs/19 §4 对账兜底口径）
                PaymentProvider.CallbackResult synthetic = new PaymentProvider.CallbackResult(
                        "query:" + orderNo, orderNo, true, o.getPayCents(), "channel-query-reconcile");
                return replaySafePay(p.name(), synthetic);
            }
        }
        return Map.of("orderNo", orderNo, "status", o.getStatus(), "channel", p.name(), "channelState", channelState);
    }

    private Map<String, Object> replaySafePay(String providerName, PaymentProvider.CallbackResult cb) {
        TradeOrder o = orderMapper.selectOne(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getOrderNo, cb.orderNo()));
        PaymentCallback record = new PaymentCallback();
        record.setOrderNo(cb.orderNo());
        record.setProvider(providerName);
        record.setCallbackId(cb.callbackId());
        record.setPaid(1);
        record.setPayload(cb.raw());
        record.setCreatedAt(LocalDateTime.now());
        try {
            callbackMapper.insert(record);
        } catch (DuplicateKeyException e) {
            return Map.of("orderNo", cb.orderNo(), "status", "PAID", "idempotent", true);
        }
        return tradeService.payNotify(cb.orderNo());
    }

    private PaymentProvider byName(String name) {
        return providers.stream()
                .filter(p -> p.name().equals(PaymentRules.normalizeProvider(name)))
                .findFirst()
                .orElseThrow(() -> new BizException(Result.NOT_FOUND, "支付渠道未启用: " + name));
    }

    private TradeOrder ownOrder(Long uid, String orderNo) {
        TradeOrder o = orderMapper.selectOne(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getOrderNo, orderNo));
        if (o == null) throw new BizException(Result.NOT_FOUND, "订单不存在");
        if (!o.getUserId().equals(uid)) throw new BizException(Result.FORBIDDEN, "无权操作该订单");
        return o;
    }
}
