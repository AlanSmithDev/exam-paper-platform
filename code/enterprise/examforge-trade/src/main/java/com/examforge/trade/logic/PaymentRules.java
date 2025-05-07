package com.examforge.trade.logic;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 支付渠道纯规则（docs/14 O-4/D14；可脱离 Spring 单测） */
public final class PaymentRules {

    public static final String MOCK = "MOCK";
    public static final String WECHAT = "WECHAT";
    public static final String ALIPAY = "ALIPAY";

    private PaymentRules() { }

    /** 渠道归一化：大小写兼容，未知回退 MOCK（沙箱口径安全缺省） */
    public static String normalizeProvider(String provider) {
        if (provider == null) return MOCK;
        return switch (provider.trim().toUpperCase()) {
            case WECHAT -> WECHAT;
            case ALIPAY -> ALIPAY;
            default -> MOCK;
        };
    }

    /** 分 → 支付宝元金额字符串（"12.34"两位小数，支付宝协议口径） */
    public static String yuanOfCents(int cents) {
        return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.UNNECESSARY).toPlainString();
    }

    /** 支付宝元金额字符串 → 分（拒绝负数/超精度；解析失败返回 -1 交由调用方拒绝） */
    public static int centsOfYuan(String yuan) {
        if (yuan == null || yuan.isBlank()) return -1;
        try {
            BigDecimal v = new BigDecimal(yuan.trim());
            if (v.signum() < 0 || v.scale() > 2) return -1;
            return v.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.UNNECESSARY).intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return -1;
        }
    }

    /**
     * 回调金额防篡改比对（docs/14 R7）：渠道回传金额必须与订单应付金额（payCents=amountCents-discountCents）一致。
     * payCents 存储口径已是应付分；不一致即拒绝（回调可重放攻击/篡改测试的核心防线）。
     */
    public static boolean amountMatches(Integer payCents, int callbackCents) {
        return payCents != null && payCents >= 0 && payCents == callbackCents;
    }

    /** 微信 V3 交易状态 → 是否已支付（SUCCESS 之外一律不算，含退款/关闭/未支付） */
    public static boolean wechatPaid(String tradeState) {
        return "SUCCESS".equalsIgnoreCase(tradeState);
    }

    /** 支付宝交易状态 → 是否已支付（TRADE_SUCCESS/TRADE_FINISHED） */
    public static boolean alipayPaid(String tradeStatus) {
        return "TRADE_SUCCESS".equalsIgnoreCase(tradeStatus) || "TRADE_FINISHED".equalsIgnoreCase(tradeStatus);
    }
}
