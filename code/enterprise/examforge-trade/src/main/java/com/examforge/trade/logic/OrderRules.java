package com.examforge.trade.logic;

/** 纯逻辑：订单状态机（docs/14 §5 O-3）+ 优惠券折扣计算，单测覆盖 */
public class OrderRules {

    public static final String CREATED = "CREATED";
    public static final String PAID = "PAID";
    public static final String CLOSED = "CLOSED";
    public static final String REFUNDED = "REFUNDED";

    /** 合法跃迁；非法返回 false */
    public static boolean canTransition(String from, String to) {
        return switch (from) {
            case CREATED -> PAID.equals(to) || CLOSED.equals(to);
            case PAID -> REFUNDED.equals(to);
            default -> false;
        };
    }

    public static void mustTransition(String from, String to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("订单状态非法跃迁: " + from + " -> " + to);
        }
    }

    /** 优惠券折扣计算：满减/折扣（封顶）/点数券；返回实付（分），不低于 0 */
    public static int applyCoupon(int amountCents, String type, Integer discountCents,
                                  Integer minSpendCents, Double discountRate, Integer rateCapCents) {
        int payable = amountCents;
        if ("FULL_REDUCTION".equals(type)) {
            if (amountCents < (minSpendCents == null ? 0 : minSpendCents)) {
                throw new IllegalArgumentException("未达到满减门槛");
            }
            payable = amountCents - (discountCents == null ? 0 : discountCents);
        } else if ("DISCOUNT".equals(type)) {
            double rate = discountRate == null ? 1.0 : discountRate;
            long discounted = Math.round(amountCents * rate);
            if (rateCapCents != null && amountCents - discounted > rateCapCents) discounted = amountCents - rateCapCents;
            payable = (int) discounted;
        } else if ("POINTS".equals(type)) {
            payable = amountCents; // 点数券不下折订单金额，支付成功后按券面值加点数
        }
        return Math.max(0, payable);
    }
}
