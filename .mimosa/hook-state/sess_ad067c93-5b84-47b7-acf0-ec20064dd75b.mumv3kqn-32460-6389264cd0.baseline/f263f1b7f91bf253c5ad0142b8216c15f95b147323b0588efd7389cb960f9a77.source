package com.examforge.paper.logic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 组卷编辑纯逻辑单测（插题/移除/默认分值/考查范围聚合，docs/26 F-XKW-05 验收） */
class PaperRulesTest {

    @Test
    void 插题_头部_中间_追加与非法位置() {
        List<Long> base = List.of(10L, 20L, 30L);
        assertEquals(List.of(99L, 10L, 20L, 30L), PaperRules.applyInsert(base, 99L, 1));      // 插最前
        assertEquals(List.of(10L, 99L, 20L, 30L), PaperRules.applyInsert(base, 99L, 2));      // 中间
        assertEquals(List.of(10L, 20L, 30L, 99L), PaperRules.applyInsert(base, 99L, 4));      // 追加=size+1
        assertEquals(List.of(10L, 20L, 30L, 99L), PaperRules.applyInsert(List.of(10L, 20L, 30L), 99L, 0 + 4), "边界=size+1");
        assertThrows(IllegalArgumentException.class, () -> PaperRules.applyInsert(base, 99L, 0));
        assertThrows(IllegalArgumentException.class, () -> PaperRules.applyInsert(base, 99L, 5));
        assertThrows(IllegalArgumentException.class, () -> PaperRules.applyInsert(List.of(), 99L, 2));
    }

    @Test
    void 插题_空卷从位置1开始() {
        assertEquals(List.of(7L), PaperRules.applyInsert(List.of(), 7L, 1));
    }

    @Test
    void 移除_不在卷中报错_存在则前移() {
        List<Long> base = List.of(10L, 20L, 30L);
        assertEquals(List.of(10L, 30L), PaperRules.applyRemove(base, 20L));
        assertEquals(List.of(20L, 30L), PaperRules.applyRemove(base, 10L));
        assertThrows(IllegalArgumentException.class, () -> PaperRules.applyRemove(base, 99L));
    }

    @Test
    void 重复入卷校验() {
        assertTrue(PaperRules.duplicated(List.of(10L, 20L), 10L));
        assertFalse(PaperRules.duplicated(List.of(10L, 20L), 30L));
    }

    @Test
    void 默认分值_按题型() {
        assertEquals(3, PaperRules.defaultScore("单选题"));
        assertEquals(3, PaperRules.defaultScore("多选题"));
        assertEquals(3, PaperRules.defaultScore("判断题"));
        assertEquals(5, PaperRules.defaultScore("填空题"));
        assertEquals(12, PaperRules.defaultScore("解答题"));
        assertEquals(5, PaperRules.defaultScore("实验探究题"));   // 未定义题型兜底
        assertEquals(5, PaperRules.defaultScore(null));
    }

    @Test
    void 考查范围_按题数降序_同频按名称_空值过滤() {
        List<Map<String, Object>> scope = PaperRules.scopeAggregation(java.util.Arrays.asList(
                "集合与常用逻辑用语,函数与导数",
                "函数与导数",
                " 数列 ，函数与导数",      // 全角逗号 + 空白
                "复数",
                null, "  "
        ));
        // 函数与导数 3 次 → 第一；集合/数列/复数各 1 次 → 名称序
        assertEquals("函数与导数", scope.get(0).get("kp"));
        assertEquals(3, scope.get(0).get("count"));
        assertEquals(4, scope.size());
        assertEquals(List.of("复数", "数列", "集合与常用逻辑用语"),
                scope.stream().skip(1).map(m -> (String) m.get("kp")).toList());
    }

    @Test
    void 考查范围_空输入() {
        assertTrue(PaperRules.scopeAggregation(List.of()).isEmpty());
        assertTrue(PaperRules.scopeAggregation(java.util.Arrays.asList(null, "", " ")).isEmpty());
    }
}
