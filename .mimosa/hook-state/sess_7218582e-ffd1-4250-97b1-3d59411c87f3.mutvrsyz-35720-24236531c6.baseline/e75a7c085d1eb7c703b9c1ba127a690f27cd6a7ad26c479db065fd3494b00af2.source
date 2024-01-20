package com.examforge.trade.payment;

import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.Result;
import com.examforge.trade.domain.TradeOrder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 沙箱渠道（docs/14 O-4）：无外呼；支付由 mock-notify 端点驱动（仅 provider=MOCK 开放） */
@Component
@ConditionalOnProperty(name = "examforge.payment.provider", havingValue = "MOCK", matchIfMissing = true)
public class MockPaymentProvider implements PaymentProvider {

    @Value("${examforge.payment.mock-notify-url:http://localhost:8080/api/v1/payments/mock-notify}")
    private String mockNotifyUrl;

    @Override
    public String name() {
        return com.examforge.trade.logic.PaymentRules.MOCK;
    }

    @Override
    public PrepayResult prepay(TradeOrder order, String returnUrl) {
        // 沙箱不产生真实凭证：返回 mock-notify 地址供测试/演示直接回调（docs/19 §4 生产禁用守卫已在端点侧）
        return new PrepayResult("MOCK_NOTIFY", null, null,
                "sandbox: POST " + mockNotifyUrl + " body {\"orderNo\":\"" + order.getOrderNo() + "\"}");
    }

    @Override
    public CallbackResult verifyCallback(Map<String, String> headers, String body, Map<String, String> form) {
        throw new BizException(Result.FORBIDDEN, "沙箱渠道不接收渠道回调（请使用 /payments/mock-notify）");
    }

    @Override
    public String queryOrder(TradeOrder order) {
        // 沙箱查单即订单本身状态（无渠道侧状态）
        return "PAID".equals(order.getStatus()) ? "PAID" : ("CLOSED".equals(order.getStatus()) ? "NOTPAID" : "UNKNOWN");
    }
}
