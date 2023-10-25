package com.examforge.trade.payment;

import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.trade.domain.PaymentCallback;
import com.examforge.trade.domain.TradeOrder;
import com.examforge.trade.mapper.PaymentCallbackMapper;
import com.examforge.trade.mapper.TradeOrderMapper;
import com.examforge.trade.service.PaymentService;
import com.examforge.trade.service.TradeService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 支付回调编排单测（docs/14 O-4/D14，R7 幂等三板斧第②板）：
 * Provider 以打桩实现替代渠道 SDK（渠道验签属 SDK 职责），聚焦可离线验证的业务规则——
 * 防重放唯一索引幂等、回调金额防篡改、未支付落流水不履约、订单归属校验。
 */
class PaymentServiceTest {

    private final TradeOrderMapper orderMapper = mock(TradeOrderMapper.class);
    private final PaymentCallbackMapper callbackMapper = mock(PaymentCallbackMapper.class);
    private final TradeService tradeService = mock(TradeService.class);

    /** 固定渠道桩：验签直接放行指定结果（真实渠道由 wechatpay-java/alipay-sdk 验签） */
    private final class StubProvider implements PaymentProvider {
        CallbackResult next = new CallbackResult("cb-1", "EF001", true, 1234, "raw");

        @Override public String name() { return com.examforge.trade.logic.PaymentRules.MOCK; }
        @Override public PrepayResult prepay(TradeOrder order, String returnUrl) {
            return new PrepayResult("MOCK_NOTIFY", null, null, "sandbox");
        }
        @Override public CallbackResult verifyCallback(Map<String, String> h, String body, Map<String, String> f) {
            return next;
        }
        @Override public String queryOrder(TradeOrder order) { return "UNKNOWN"; }
    }

    private final StubProvider stub = new StubProvider();
    private final PaymentService service = new PaymentService(orderMapper, callbackMapper, tradeService, List.of(stub));

    private TradeOrder order(String orderNo, String status, Integer payCents) {
        TradeOrder o = new TradeOrder();
        o.setId(1L);
        o.setOrderNo(orderNo);
        o.setUserId(100L);
        o.setSkuType("MEMBER");
        o.setPayCents(payCents);
        o.setStatus(status);
        return o;
    }

    @Test
    void 首次有效回调_落流水并幂等支付() {
        TradeOrder o = order("EF001", "CREATED", 1234);
        when(orderMapper.selectOne(any())).thenReturn(o);
        when(tradeService.payNotify("EF001")).thenReturn(Map.of("orderNo", "EF001", "status", "PAID"));

        Map<String, Object> out = service.handleCallback("MOCK", Map.of(), "raw-body", Map.of());

        assertEquals("PAID", out.get("status"));
        ArgumentCaptor<PaymentCallback> captor = ArgumentCaptor.forClass(PaymentCallback.class);
        verify(callbackMapper).insert(captor.capture());
        assertEquals("cb-1", captor.getValue().getCallbackId());
        assertEquals(1, captor.getValue().getPaid());
        verify(tradeService, times(1)).payNotify("EF001");
    }

    @Test
    void 回调重放_唯一索引冲突_幂等返回不再履约() {
        TradeOrder o = order("EF001", "PAID", 1234);
        when(orderMapper.selectOne(any())).thenReturn(o);
        when(callbackMapper.insert(any(PaymentCallback.class)))
                .thenThrow(new DuplicateKeyException("uk_provider_callback"));

        Map<String, Object> out = service.handleCallback("MOCK", Map.of(), "raw-body", Map.of());

        assertEquals("PAID", out.get("status"));
        assertTrue((Boolean) out.get("idempotent"));
        verify(tradeService, never()).payNotify(any());
    }

    @Test
    void 回调金额与订单不符_标记未采信并拒绝履约() {
        when(orderMapper.selectOne(any())).thenReturn(order("EF001", "CREATED", 1234));
        stub.next = new PaymentProvider.CallbackResult("cb-2", "EF001", true, 99999, "raw");

        assertThrows(BizException.class, () -> service.handleCallback("MOCK", Map.of(), "raw", Map.of()),
                "金额防篡改：回调金额必须精确等于订单应付");
        verify(callbackMapper).insert(any(PaymentCallback.class));   // 留审计流水
        ArgumentCaptor<PaymentCallback> captor = ArgumentCaptor.forClass(PaymentCallback.class);
        verify(callbackMapper).updateById(captor.capture());         // 标记未采信
        assertEquals(0, captor.getValue().getPaid());
        verify(tradeService, never()).payNotify(any());
    }

    @Test
    void 回调未支付_仅落流水不履约() {
        when(orderMapper.selectOne(any())).thenReturn(order("EF001", "CREATED", 1234));
        stub.next = new PaymentProvider.CallbackResult("cb-3", "EF001", false, 1234, "raw");

        Map<String, Object> out = service.handleCallback("MOCK", Map.of(), "raw", Map.of());

        assertEquals(false, out.get("paid"));
        verify(tradeService, never()).payNotify(any());
    }

    @Test
    void 回调订单不存在_拒绝() {
        when(orderMapper.selectOne(any())).thenReturn(null);
        assertThrows(BizException.class, () -> service.handleCallback("MOCK", Map.of(), "raw", Map.of()));
    }

    @Test
    void prepay_非本人订单_拒绝() {
        when(orderMapper.selectOne(any())).thenReturn(order("EF001", "CREATED", 1234));
        assertThrows(BizException.class, () -> service.prepay(999L, "EF001", null));
    }

    @Test
    void prepay_已支付订单_拒绝重复下单() {
        when(orderMapper.selectOne(any())).thenReturn(order("EF001", "PAID", 1234));
        assertThrows(BizException.class, () -> service.prepay(100L, "EF001", null));
    }

    @Test
    void 渠道装配异常_多实现拒绝() {
        PaymentService bad = new PaymentService(orderMapper, callbackMapper, tradeService,
                List.of(stub, new StubProvider()));
        when(orderMapper.selectOne(any())).thenReturn(order("EF001", "CREATED", 1234));
        assertThrows(BizException.class, () -> bad.prepay(100L, "EF001", null));
    }
}
