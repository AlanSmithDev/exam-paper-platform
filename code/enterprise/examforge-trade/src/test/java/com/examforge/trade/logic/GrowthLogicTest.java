package com.examforge.trade.logic;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 用户增长域纯逻辑单测（CDK 码/批次规则/任务周期，docs/26 T-26d 验收） */
class GrowthLogicTest {

    @Test
    void 激活码_生成格式与去歧义字符集() {
        for (int i = 0; i < 200; i++) {
            String code = GrowthRules.generateCode();
            assertEquals("XXXX-XXXX-XXXX".length(), code.length());
            assertTrue(GrowthRules.validCode(GrowthRules.normalize(code)), code + " 应通过格式校验");
            assertFalse(code.contains("0") || code.contains("O") || code.contains("1") || code.contains("I")
                    || code.contains("L"), code + " 不应含歧义字符");
        }
    }

    @Test
    void 激活码_归一化容忍大小写横线与空格() {
        assertEquals(GrowthRules.normalize("ab23-4567-89cd"), "AB23456789CD");
        assertEquals(GrowthRules.normalize(" AB 23 45 67 89 cd "), "AB23456789CD");
        assertFalse(GrowthRules.validCode("AB23456789C"));    // 11 位
        assertFalse(GrowthRules.validCode("AB23456789CD0"));  // 13 位
        assertFalse(GrowthRules.validCode("AB23O56789CD"));   // 含歧义字符 O
    }

    @Test
    void 批次校验_三种奖励类型与非法参数() {
        assertDoesNotThrow(() -> GrowthRules.validateBatch(GrowthRules.TYPE_MEMBER_DAYS, 30, null, null, 100));
        assertDoesNotThrow(() -> GrowthRules.validateBatch(GrowthRules.TYPE_POINTS, null, 500, null, 100));
        assertDoesNotThrow(() -> GrowthRules.validateBatch(GrowthRules.TYPE_COUPON, null, null, 3L, 100));
        assertThrows(IllegalArgumentException.class,
                () -> GrowthRules.validateBatch(GrowthRules.TYPE_MEMBER_DAYS, 400, null, null, 100));
        assertThrows(IllegalArgumentException.class,
                () -> GrowthRules.validateBatch(GrowthRules.TYPE_POINTS, null, 0, null, 100));
        assertThrows(IllegalArgumentException.class,
                () -> GrowthRules.validateBatch(GrowthRules.TYPE_COUPON, null, null, null, 100));
        assertThrows(IllegalArgumentException.class,
                () -> GrowthRules.validateBatch("FREE_VIP", 30, null, null, 100));
        assertThrows(IllegalArgumentException.class,
                () -> GrowthRules.validateBatch(GrowthRules.TYPE_MEMBER_DAYS, 30, null, null, 5001));
    }

    @Test
    void 任务周期_每日任务按天_一次性终身() {
        assertEquals("20260928", GrowthRules.periodOf(true, LocalDate.of(2026, 9, 28)));
        assertEquals("LIFETIME", GrowthRules.periodOf(false, LocalDate.of(2026, 9, 28)));
    }

    @Test
    void 兑换窗口_停用与过期判定() {
        GrowthRules.CdkBatchView active = view(1, null);
        GrowthRules.CdkBatchView disabled = view(0, null);
        GrowthRules.CdkBatchView expired = view(1, LocalDateTime.now().minusDays(1));
        assertTrue(GrowthRules.redeemable(active, LocalDateTime.now()));
        assertFalse(GrowthRules.redeemable(disabled, LocalDateTime.now()));
        assertFalse(GrowthRules.redeemable(expired, LocalDateTime.now()));
        assertFalse(GrowthRules.redeemable(null, LocalDateTime.now()));
    }

    @Test
    void 激活码随机性_200张无重复() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 200; i++) codes.add(GrowthRules.generateCode());
        assertEquals(200, codes.size());
    }

    private GrowthRules.CdkBatchView view(int status, LocalDateTime expire) {
        return new GrowthRules.CdkBatchView() {
            @Override public Integer status() { return status; }
            @Override public LocalDateTime expireTime() { return expire; }
        };
    }
}
