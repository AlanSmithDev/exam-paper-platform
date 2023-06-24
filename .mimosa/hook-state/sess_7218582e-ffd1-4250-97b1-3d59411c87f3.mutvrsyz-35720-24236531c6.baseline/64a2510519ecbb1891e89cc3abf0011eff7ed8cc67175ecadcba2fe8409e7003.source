package com.examforge.resource.logic;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 资源域纯逻辑单测（预览 33% / 判价 / 申诉状态机 / 结算月份解析，docs/26 F-XKW-01/02/14 验收） */
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

    @Test
    void 结算月份解析_空用fallback_非法抛出() {
        YearMonth fb = YearMonth.of(2026, 8);
        assertEquals(YearMonth.of(2026, 9), ResourceRules.parseMonth("2026-09", fb));
        assertEquals(fb, ResourceRules.parseMonth(null, fb));      // 调度器缺省路径
        assertEquals(fb, ResourceRules.parseMonth("", fb));
        assertEquals(fb, ResourceRules.parseMonth("  ", fb));
        assertThrows(IllegalArgumentException.class, () -> ResourceRules.parseMonth("2026/09", fb));
        assertThrows(IllegalArgumentException.class, () -> ResourceRules.parseMonth("2026-13", fb));
    }

    @Test
    void 签约合同有效期_状态与时间窗判定() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 29, 12, 0);
        assertTrue(ResourceRules.contractActive("ACTIVE", null, null, now));                     // 长期有效
        assertTrue(ResourceRules.contractActive("ACTIVE", now.minusDays(1), null, now));
        assertTrue(ResourceRules.contractActive("ACTIVE", null, now.plusDays(1), now));
        assertFalse(ResourceRules.contractActive("ACTIVE", now.plusDays(1), null, now));          // 未生效
        assertFalse(ResourceRules.contractActive("ACTIVE", null, now, now));                      // 到期边界（end 不含）
        assertFalse(ResourceRules.contractActive("ENDED", null, null, now));                      // 已解约
    }

    @Test
    void 分成比例_签约优先_否则全局_均夹取() {
        assertEquals(70, ResourceRules.resolveSharePct(70, 50));    // 签约比例覆盖
        assertEquals(50, ResourceRules.resolveSharePct(null, 50));  // 无签约用全局
        assertEquals(100, ResourceRules.resolveSharePct(150, 50));  // 签约比例夹取上限
        assertEquals(0, ResourceRules.resolveSharePct(-10, 50));    // 夹取下限
        assertEquals(100, ResourceRules.resolveSharePct(null, 180)); // 全局比例夹取上限
    }

    @Test
    void 资源篮单件判定_未上架跳过_已购免费计费() {
        assertEquals(ResourceRules.BasketMode.SKIP, ResourceRules.basketMode("NORMAL", false, false));
        assertEquals(ResourceRules.BasketMode.SKIP, ResourceRules.basketMode("FREE", false, false));
        assertEquals(ResourceRules.BasketMode.OWNED, ResourceRules.basketMode("NORMAL", true, true));
        assertEquals(ResourceRules.BasketMode.OWNED, ResourceRules.basketMode("BOUTIQUE", true, true));  // 已购不再计费
        assertEquals(ResourceRules.BasketMode.FREE, ResourceRules.basketMode("FREE", false, true));
        assertEquals(ResourceRules.BasketMode.POINTS, ResourceRules.basketMode("SPECIAL", false, true));
    }

    @Test
    void 版权工单SLA_空数据全零() {
        var s = ResourceRules.appealSlaStats(List.of(), LocalDateTime.of(2026, 9, 29, 12, 0), 72);
        assertEquals(0, s.get("total"));
        assertEquals(0, s.get("open"));
        assertEquals(0, s.get("closed"));
        assertEquals(0.0, s.get("avgResolveHours"));
        assertEquals(0, s.get("overdueOpen"));
        assertEquals(0.0, s.get("overdueRatePct"));
    }

    @Test
    void 版权工单SLA_超时未处理与平均处理时长() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 29, 12, 0);
        List<ResourceRules.SlaRow> rows = List.of(
                new ResourceRules.SlaRow("OPEN", now.minusHours(100), null),                 // 超时（>72h）
                new ResourceRules.SlaRow("OPEN", now.minusHours(10), null),                  // 未超时
                new ResourceRules.SlaRow("RESOLVED", now.minusHours(48), now.minusHours(24)), // 处理时长 24h
                new ResourceRules.SlaRow("REJECTED", now.minusHours(30), now.minusHours(6))); // 处理时长 24h
        var s = ResourceRules.appealSlaStats(rows, now, 72);
        assertEquals(4, s.get("total"));
        assertEquals(2, s.get("open"));
        assertEquals(2, s.get("closed"));
        assertEquals(24.0, s.get("avgResolveHours"));
        assertEquals(1, s.get("overdueOpen"));
        assertEquals(50.0, s.get("overdueRatePct"));
        assertEquals(72, s.get("slaHours"));
    }
}
