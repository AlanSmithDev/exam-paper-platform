package com.examforge.trade.payment;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.Result;
import com.examforge.trade.domain.TradeOrder;
import com.examforge.trade.logic.PaymentRules;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 支付宝电脑网站支付渠道（官方 alipay-sdk-java，docs/14 O-4/D14）。
 * 异步通知 RSA2 验签（AlipaySignature.rsaCheckV1，支付宝公钥模式）+ 主动查单兜底。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "examforge.payment.provider", havingValue = "ALIPAY")
public class AlipayProvider implements PaymentProvider {

    private final AlipayClient client;
    private final String alipayPublicKey;
    private final String notifyUrl;

    public AlipayProvider(@Value("${examforge.payment.alipay.app-id}") String appId,
                          @Value("${examforge.payment.alipay.private-key}") String privateKey,
                          @Value("${examforge.payment.alipay.alipay-public-key}") String alipayPublicKey,
                          @Value("${examforge.payment.alipay.gateway:https://openapi.alipay.com/gateway}") String gateway,
                          @Value("${examforge.payment.alipay.notify-url}") String notifyUrl) {
        this.alipayPublicKey = alipayPublicKey;
        this.notifyUrl = notifyUrl;
        this.client = new DefaultAlipayClient(gateway, appId, privateKey, "json", "UTF-8", alipayPublicKey, "RSA2");
        log.info("支付宝渠道已启用: appId={} notify={}", appId, notifyUrl);
    }

    @Override
    public String name() {
        return PaymentRules.ALIPAY;
    }

    @Override
    public PrepayResult prepay(TradeOrder order, String returnUrl) {
        AlipayTradePagePayRequest req = new AlipayTradePagePayRequest();
        req.setNotifyUrl(notifyUrl);
        if (returnUrl != null && !returnUrl.isBlank()) req.setReturnUrl(returnUrl);
        String totalYuan = PaymentRules.yuanOfCents(order.getPayCents() == null ? order.getAmountCents() : order.getPayCents());
        req.setBizContent("{\"out_trade_no\":\"" + order.getOrderNo() + "\",\"total_amount\":\"" + totalYuan
                + "\",\"subject\":\"智卷云 " + order.getSkuType() + " " + order.getOrderNo()
                + "\",\"product_code\":\"FAST_INSTANT_TRADE_PAY\"}");
        try {
            // GET 方式返回可跳转 URL（POST 方式返回自动提交表单）
            AlipayTradePagePayResponse resp = client.pageExecute(req, "GET");
            return new PrepayResult("REDIRECT", null, resp.getBody(), totalYuan);
        } catch (AlipayApiException e) {
            log.warn("支付宝下单失败 order={}: {}", order.getOrderNo(), e.getMessage());
            throw new BizException(Result.SYSTEM, "支付下单失败，请稍后重试");
        }
    }

    @Override
    public CallbackResult verifyCallback(Map<String, String> headers, String body, Map<String, String> form) {
        try {
            // RSA2 验签（支付宝异步通知口径：剔除 sign/sign_type 后验签）
            boolean signOk = AlipaySignature.rsaCheckV1(new java.util.HashMap<>(form), alipayPublicKey, "UTF-8", "RSA2");
            if (!signOk) {
                log.warn("支付宝回调验签失败 notify_id={}", form.get("notify_id"));
                throw new BizException(Result.UNAUTHORIZED, "支付宝回调验证失败");
            }
            int amountCents = PaymentRules.centsOfYuan(form.get("total_amount"));
            return new CallbackResult(form.get("notify_id"), form.get("out_trade_no"),
                    PaymentRules.alipayPaid(form.get("trade_status")), amountCents < 0 ? null : amountCents,
                    form.toString());
        } catch (AlipayApiException e) {
            log.warn("支付宝回调验签异常: {}", e.getMessage());
            throw new BizException(Result.UNAUTHORIZED, "支付宝回调验证失败");
        }
    }

    @Override
    public String queryOrder(TradeOrder order) {
        AlipayTradeQueryRequest req = new AlipayTradeQueryRequest();
        req.setBizContent("{\"out_trade_no\":\"" + order.getOrderNo() + "\"}");
        try {
            AlipayTradeQueryResponse resp = client.execute(req);
            if (resp == null || resp.getTradeStatus() == null) return "NOTPAID";   // 交易不存在=未支付
            return PaymentRules.alipayPaid(resp.getTradeStatus()) ? "PAID" : "NOTPAID";
        } catch (AlipayApiException e) {
            log.warn("支付宝查单失败 order={}: {}", order.getOrderNo(), e.getMessage());
            return "UNKNOWN";
        }
    }
}
