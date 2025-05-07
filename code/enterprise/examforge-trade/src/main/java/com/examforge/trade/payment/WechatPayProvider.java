package com.examforge.trade.payment;

import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.Result;
import com.examforge.trade.domain.TradeOrder;
import com.examforge.trade.logic.PaymentRules;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.Amount;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;
import com.wechat.pay.java.service.payments.nativepay.model.QueryOrderByOutTradeNoRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 微信支付 Native 扫码渠道（官方 wechatpay-java V3 SDK，docs/14 O-4/D14）。
 * 平台证书由 RSAAutoCertificateConfig 自动下载轮换；回调经 NotificationParser 验签+AES-GCM 解密。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "examforge.payment.provider", havingValue = "WECHAT")
public class WechatPayProvider implements PaymentProvider {

    private final String appId;
    private final String merchantId;
    private final String notifyUrl;
    private final NativePayService nativePayService;
    private final NotificationParser notificationParser;

    public WechatPayProvider(@Value("${examforge.payment.wechat.merchant-id}") String merchantId,
                             @Value("${examforge.payment.wechat.merchant-serial-no}") String merchantSerialNo,
                             @Value("${examforge.payment.wechat.api-v3-key}") String apiV3Key,
                             @Value("${examforge.payment.wechat.private-key-path}") String privateKeyPath,
                             @Value("${examforge.payment.wechat.app-id}") String appId,
                             @Value("${examforge.payment.wechat.notify-url}") String notifyUrl) {
        this.appId = appId;
        this.merchantId = merchantId;
        this.notifyUrl = notifyUrl;
        RSAAutoCertificateConfig config = new RSAAutoCertificateConfig.Builder()
                .merchantId(merchantId)
                .privateKeyFromPath(privateKeyPath)
                .merchantSerialNumber(merchantSerialNo)
                .apiV3Key(apiV3Key)
                .build();
        this.nativePayService = new NativePayService.Builder().config(config).build();
        this.notificationParser = new NotificationParser((NotificationConfig) config);
        log.info("微信支付渠道已启用: mch={} notify={}", merchantId, notifyUrl);
    }

    @Override
    public String name() {
        return PaymentRules.WECHAT;
    }

    @Override
    public PrepayResult prepay(TradeOrder order, String returnUrl) {
        PrepayRequest req = new PrepayRequest();
        req.setAppid(appId);
        req.setMchid(merchantId);
        req.setDescription(paySubject(order));
        req.setOutTradeNo(order.getOrderNo());
        req.setNotifyUrl(notifyUrl);
        Amount amount = new Amount();
        amount.setTotal(order.getPayCents() == null ? order.getAmountCents() : order.getPayCents());
        req.setAmount(amount);
        PrepayResponse resp = nativePayService.prepay(req);
        return new PrepayResult("QR", resp.getCodeUrl(), null, resp.getCodeUrl());
    }

    @Override
    public CallbackResult verifyCallback(Map<String, String> headers, String body, Map<String, String> form) {
        try {
            RequestParam param = new RequestParam.Builder()
                    .serialNumber(headers.get("Wechatpay-Serial"))
                    .nonce(headers.get("Wechatpay-Nonce"))
                    .signature(headers.get("Wechatpay-Signature"))
                    .timestamp(headers.get("Wechatpay-Timestamp"))
                    .body(body)
                    .build();
            Transaction txn = notificationParser.parse(param, Transaction.class);
            Integer total = txn.getAmount() == null ? null : txn.getAmount().getTotal();
            String callbackId = headers.getOrDefault("Request-ID", txn.getTransactionId());
            return new CallbackResult(callbackId, txn.getOutTradeNo(),
                    PaymentRules.wechatPaid(String.valueOf(txn.getTradeState())), total, body);
        } catch (Exception e) {
            log.warn("微信回调验签/解密失败: {}", e.getMessage());
            throw new BizException(Result.UNAUTHORIZED, "微信回调验证失败");
        }
    }

    @Override
    public String queryOrder(TradeOrder order) {
        QueryOrderByOutTradeNoRequest req = new QueryOrderByOutTradeNoRequest();
        req.setMchid(merchantId);
        req.setOutTradeNo(order.getOrderNo());
        try {
            Transaction txn = nativePayService.queryOrderByOutTradeNo(req);
            return PaymentRules.wechatPaid(String.valueOf(txn.getTradeState())) ? "PAID" : "NOTPAID";
        } catch (Exception e) {
            log.warn("微信查单失败 order={}: {}", order.getOrderNo(), e.getMessage());
            return "UNKNOWN";
        }
    }

    private String paySubject(TradeOrder order) {
        return "智卷云 " + order.getSkuType() + " " + order.getOrderNo();
    }

}
