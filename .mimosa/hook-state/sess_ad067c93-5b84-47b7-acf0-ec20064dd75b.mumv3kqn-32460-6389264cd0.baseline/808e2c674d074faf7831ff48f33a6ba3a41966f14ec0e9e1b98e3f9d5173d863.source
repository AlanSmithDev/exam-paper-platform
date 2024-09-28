package com.examforge.trade.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 计价与状态机纯逻辑单测（docs/14 §9 验收 2/5/6） */
class TradeLogicTest {

    @Test
    void 按题量计价_对标组卷网且便宜一半() {
        assertEquals(100, PriceCalculator.paperPriceCents(1));
        assertEquals(100, PriceCalculator.paperPriceCents(10));
        assertEquals(200, PriceCalculator.paperPriceCents(19));
        assertEquals(400, PriceCalculator.paperPriceCents(50));
        assertEquals(600, PriceCalculator.paperPriceCents(100));
    }

    @Test
    void 判价优先级_重复下载_会员_免费额度_点数() {
        assertEquals(PriceCalculator.Mode.FREE, PriceCalculator.decide(true, false, 3, 0, 10).mode());
        assertEquals(PriceCalculator.Mode.MEMBER, PriceCalculator.decide(false, true, 0, 0, 10).mode());
        assertEquals(PriceCalculator.Mode.FREE, PriceCalculator.decide(false, false, 2, 0, 10).mode());
        PriceCalculator.Decision d = PriceCalculator.decide(false, false, 0, 500, 10);
        assertEquals(PriceCalculator.Mode.POINTS, d.mode());
        assertEquals(100, d.needPoints());
        assertTrue(d.reason().contains("点数下载"));
    }

    @Test
    void 状态机_非法跃迁被拒绝() {
        assertTrue(OrderRules.canTransition(OrderRules.CREATED, OrderRules.PAID));
        assertTrue(OrderRules.canTransition(OrderRules.CREATED, OrderRules.CLOSED));
        assertTrue(OrderRules.canTransition(OrderRules.PAID, OrderRules.REFUNDED));
        assertFalse(OrderRules.canTransition(OrderRules.PAID, OrderRules.PAID));
        assertFalse(OrderRules.canTransition(OrderRules.CLOSED, OrderRules.PAID));
        assertThrows(IllegalStateException.class, () -> OrderRules.mustTransition(OrderRules.PAID, OrderRules.PAID));
    }

    @Test
    void 优惠券_满减门槛与折扣封顶() {
        assertEquals(500, OrderRules.applyCoupon(1000, "FULL_REDUCTION", 500, 800, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> OrderRules.applyCoupon(500, "FULL_REDUCTION", 500, 800, null, null));
        // 8 折封顶 150：1000*0.8=800，优惠 200 超过封顶 → 优惠 150，实付 850
        assertEquals(850, OrderRules.applyCoupon(1000, "DISCOUNT", null, 0, 0.8, 150));
        assertEquals(0, OrderRules.applyCoupon(300, "FULL_REDUCTION", 500, 0, null, null)); // 不为负
    }
}
