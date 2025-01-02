package com.examforge.resource.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 资源域纯逻辑单测（预览 33% / 判价 / 申诉状态机，docs/26 F-XKW-01/02/14 验收） */
class ResourceRulesTest {

    @Test
    void 预览_免费页数按33且至少1页() {
        assertEquals(15, ResourceRules.previewFreePages(48, 33));   // 48*0.33=15.84 → 15
        assertEquals(1, ResourceRules.previewFreePages(1, 33));
        assertEquals(1, ResourceRules.previewFreePages(2, 33));
        assertEquals(0, ResourceRules.previewFreePages(0, 33));     // 无页数不可预览
        assertEquals(2, ResourceRules.previewFreePages(9, 33));     // 9*0.33=2.97 → 2
    }

    @Test
    void 预览_百分比夹取() {
        assertEquals(1, ResourceRules.previewFreePages(100, 0));    // pct<1 夹到 1 → 1 页
        assertEquals(100, ResourceRules.previewFreePages(100, 150)); // >100 夹到 100 → 全部
    }

    @Test
    void 判价_免费档_已购_余额足够_不足() {
        assertEquals(ResourceRules.Mode.FREE, ResourceRules.decide("FREE", false, 0, 0));
        assertEquals(ResourceRules.Mode.FREE, ResourceRules.decide("NORMAL", true, 0, 200));
        assertEquals(ResourceRules.Mode.POINTS, ResourceRules.decide("NORMAL", false, 500, 200));
        assertEquals(ResourceRules.Mode.INSUFFICIENT, ResourceRules.decide("BOUTIQUE", false, 100, 500));
        assertEquals(ResourceRules.Mode.FREE, ResourceRules.decide("FREE", true, 0, 0));
    }

    @Test
    void 价格校验_免费档必须0_收费档必须为正() {
        assertDoesNotThrow(() -> ResourceRules.validatePricing("FREE", 0));
        assertThrows(IllegalArgumentException.class, () -> ResourceRules.validatePricing("FREE", 200));
        assertDoesNotThrow(() -> ResourceRules.validatePricing("SPECIAL", 300));
        assertThrows(IllegalArgumentException.class, () -> ResourceRules.validatePricing("SPECIAL", 0));
        assertThrows(IllegalArgumentException.class, () -> ResourceRules.validatePricing("BOUTIQUE", 200000));
    }

    @Test
    void 分成_按比例向下取整() {
        assertEquals(100, ResourceRules.shareCents(200, 50));
        assertEquals(150, ResourceRules.shareCents(500, 30));
        assertEquals(4, ResourceRules.shareCents(9, 50));      // 4.5 → 4
        assertEquals(0, ResourceRules.shareCents(1, 50));      // 0.5 → 0
        assertEquals(0, ResourceRules.shareCents(0, 50));
        assertEquals(200, ResourceRules.shareCents(200, 100));
    }

    @Test
    void 分成_比例夹取0到100() {
        assertEquals(0, ResourceRules.shareCents(200, -5));    // 负比例夹到 0
        assertEquals(200, ResourceRules.shareCents(200, 150)); // >100 夹到 100
    }

    @Test
    void 申诉状态机_OPEN可迁出_终态不可变() {
        assertTrue(ResourceRules.canTransition("OPEN", "RESOLVED"));
        assertTrue(ResourceRules.canTransition("OPEN", "REJECTED"));
        assertTrue(ResourceRules.canTransition("OPEN", "WITHDRAWN"));
        assertFalse(ResourceRules.canTransition("RESOLVED", "OPEN"));
        assertFalse(ResourceRules.canTransition("RESOLVED", "REJECTED"));
        assertFalse(ResourceRules.canTransition("WITHDRAWN", "RESOLVED"));
        assertThrows(IllegalStateException.class, () -> ResourceRules.mustTransition("RESOLVED", "RESOLVED"));
        assertDoesNotThrow(() -> ResourceRules.mustTransition("OPEN", "RESOLVED"));
    }
}
