package com.examforge.trade.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 支付渠道纯规则单测（docs/14 O-4/D14 验收） */
class PaymentRulesTest {

    @Test
    void 渠道归一化_大小写与安全缺省() {
        assertEquals("WECHAT", PaymentRules.normalizeProvider("wechat"));
        assertEquals("ALIPAY", PaymentRules.normalizeProvider(" ALIPAY "));
        assertEquals("MOCK", PaymentRules.normalizeProvider(null));
        assertEquals("MOCK", PaymentRules.normalizeProvider("paypal"), "未知渠道回退沙箱（安全缺省）");
    }

    @Test
    void 分元转换_两位小数协议口径() {
        assertEquals("12.34", PaymentRules.yuanOfCents(1234));
        assertEquals("0.01", PaymentRules.yuanOfCents(1));
        assertEquals("88.00", PaymentRules.yuanOfCents(8800));
        assertEquals(1234, PaymentRules.centsOfYuan("12.34"));
        assertEquals(1, PaymentRules.centsOfYuan("0.01"));
        assertEquals(8800, PaymentRules.centsOfYuan("88.0"));
        assertEquals(-1, PaymentRules.centsOfYuan("-1.00"), "负数拒绝");
        assertEquals(-1, PaymentRules.centsOfYuan("12.345"), "超两位小数拒绝");
        assertEquals(-1, PaymentRules.centsOfYuan("abc"), "非法数值拒绝");
        assertEquals(-1, PaymentRules.centsOfYuan(null));
    }

    @Test
    void 回调金额防篡改_与订单应付一致才通过() {
        assertTrue(PaymentRules.amountMatches(1234, 1234));
        assertFalse(PaymentRules.amountMatches(1234, 1), "少付拒绝");
        assertFalse(PaymentRules.amountMatches(1234, 12340), "多付也拒绝（金额必须精确一致）");
        assertFalse(PaymentRules.amountMatches(null, 100), "订单无应付金额拒绝");
        assertTrue(PaymentRules.amountMatches(0, 0), "0 元单金额一致仍通过（正常单经渠道，免费走 FREE 判价）");
    }

    @Test
    void 渠道交易状态映射() {
        assertTrue(PaymentRules.wechatPaid("SUCCESS"));
        assertTrue(PaymentRules.wechatPaid("success"));
        assertFalse(PaymentRules.wechatPaid("NOTPAY"));
        assertFalse(PaymentRules.wechatPaid("REFUND"), "退款不算已支付");
        assertFalse(PaymentRules.wechatPaid(null));
        assertTrue(PaymentRules.alipayPaid("TRADE_SUCCESS"));
        assertTrue(PaymentRules.alipayPaid("TRADE_FINISHED"));
        assertFalse(PaymentRules.alipayPaid("WAIT_BUYER_PAY"));
        assertFalse(PaymentRules.alipayPaid(null));
    }
}
