package com.examforge.trade.payment;

import com.examforge.trade.domain.TradeOrder;

import java.util.Map;

/**
 * 支付渠道 SPI（docs/14 O-4）：MOCK 沙箱 / WECHAT 微信支付 V3 / ALIPAY 支付宝电脑网站支付。
 * 渠道实现为条件装配（examforge.payment.provider），未配置凭证的渠道不创建 Bean。
 */
public interface PaymentProvider {

    /** 渠道标识（PaymentRules 常量） */
    String name();

    /**
     * 渠道下单：订单必须 CREATED 状态（调用方校验）。
     * 返回支付凭证：微信 Native 为 codeUrl（前端渲染二维码）；支付宝为 payUrl（跳转）。
     */
    PrepayResult prepay(TradeOrder order, String returnUrl);

    /**
     * 渠道回调验证与解析：验签失败抛 BizException（40100/42200，渠道侧会重试）。
     * 成功返回 callbackId（防重放幂等键）+ orderNo + paid + amountCents（供金额比对防篡改）。
     */
    CallbackResult verifyCallback(Map<String, String> headers, String body, Map<String, String> form);

    /** 主动查单兜底（docs/19 §4 对账/查单）：返回渠道侧支付状态 PAID/NOTPAID/UNKNOWN */
    String queryOrder(TradeOrder order);

    /** 下单结果 */
    record PrepayResult(String mode, String codeUrl, String payUrl, String raw) { }

    /** 回调解析结果（验签之后、落库之前） */
    record CallbackResult(String callbackId, String orderNo, boolean paid, Integer amountCents, String raw) { }
}
