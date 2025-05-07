package com.examforge.school.logic;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 学校订阅纯规则单测（T-26h 验收：docs/26 §7 学校服务线） */
class SchoolRulesTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 9, 30, 10, 0);

    @Test
    void 订阅有效性_状态与到期判定() {
        assertTrue(SchoolRules.isActive(1, now.plusDays(30), now));
        assertFalse(SchoolRules.isActive(0, now.plusDays(30), now), "关闭状态无效");
        assertFalse(SchoolRules.isActive(1, now.minusSeconds(1), now), "已到期无效");
        assertFalse(SchoolRules.isActive(1, null, now), "未设置到期视为未生效");
    }

    @Test
    void 席位校验_上限夹取与不限() {
        assertTrue(SchoolRules.canAddTeacher(9, 10));
        assertFalse(SchoolRules.canAddTeacher(10, 10));
        assertFalse(SchoolRules.canAddTeacher(15, 10));
        assertTrue(SchoolRules.canAddTeacher(500, null), "未设席位视为不限");
    }

    @Test
    void 批量成员_去空去重保序与上限() {
        assertEquals(List.of(3L, 1L, 2L), SchoolRules.normalizeBatch(java.util.Arrays.asList(3L, 1L, 3L, null, 2L, 1L)));
        assertEquals(List.of(), SchoolRules.normalizeBatch(null));
        assertEquals(List.of(), SchoolRules.normalizeBatch(java.util.Arrays.asList(null, null)));
        // 超过单次上限截断到 100 且保序
        List<Long> many = java.util.stream.LongStream.rangeClosed(1, 150).mapToObj(Long::valueOf).toList();
        List<Long> out = SchoolRules.normalizeBatch(many);
        assertEquals(SchoolRules.MAX_BATCH, out.size());
        assertEquals(1L, out.get(0));
        assertEquals(100L, out.get(99));
    }

    @Test
    void 角色归一化_大小写与缺省教师() {
        assertEquals("TEACHER", SchoolRules.normalizeRole("teacher"));
        assertEquals("STUDENT", SchoolRules.normalizeRole(" STUDENT "));
        assertEquals("TEACHER", SchoolRules.normalizeRole(null));
        assertEquals("TEACHER", SchoolRules.normalizeRole("校长"), "非法角色缺省教师（权益核心口径）");
    }

    @Test
    void 续订_未到期顺延_已到期从现在起算() {
        assertEquals(now.plusDays(30).plusMonths(12),
                SchoolRules.renewUntil(now.plusDays(30), 12, now), "未到期续订在原到期时间上顺延");
        assertEquals(now.plusMonths(12),
                SchoolRules.renewUntil(now.minusDays(1), 12, now), "已到期从当前时间起算");
        assertEquals(now.plusMonths(1), SchoolRules.renewUntil(now, 0, now), "非法月数按 1 个月");
    }
}
