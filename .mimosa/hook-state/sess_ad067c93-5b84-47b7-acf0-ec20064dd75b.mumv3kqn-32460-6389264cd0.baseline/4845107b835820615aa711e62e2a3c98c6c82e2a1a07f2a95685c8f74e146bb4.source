package com.examforge.question.logic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 素养标签纯规则单测（T-26a，docs/27 §6：关键词映射初打 / 合并 / 六维校验） */
class LiteracyRulesTest {

    @Test
    void 自动打标_关键词命中与六维排序() {
        assertEquals(List.of("数学运算", "直观想象"), LiteracyRules.autoTag("立体几何,函数求值"));  // 按六维顺序
        assertEquals(List.of("数据分析"), LiteracyRules.autoTag("统计与概率,随机变量"));
        assertEquals(List.of("数学建模", "数据分析"), LiteracyRules.autoTag("实际应用,数据分析"));
        assertEquals(List.of("抽象概括"), LiteracyRules.autoTag("圆锥曲线第二定义"));  // "定义"命中映射
        assertEquals(List.of(), LiteracyRules.autoTag("数系扩充"));  // 未命中任何关键词不猜
        assertEquals(List.of(), LiteracyRules.autoTag(null));
        assertEquals(List.of(), LiteracyRules.autoTag(""));
    }

    @Test
    void 自动打标_多知识点去重() {
        List<String> tags = LiteracyRules.autoTag("函数概念,函数运算,逻辑推理");
        assertEquals(List.of("数学运算", "逻辑推理", "抽象概括"), tags);  // 三个 kp 共四命中，去重后按六维排序
    }

    @Test
    void 合并_已有素养与自动打标去重保序() {
        assertEquals("数学运算,逻辑推理", LiteracyRules.merge("数学运算", List.of("逻辑推理", "数学运算")));
        assertEquals("逻辑推理,直观想象", LiteracyRules.merge(null, List.of("直观想象", "逻辑推理")));
        assertEquals("数据分析", LiteracyRules.merge("数据分析", List.of()));
        assertEquals("", LiteracyRules.merge(null, List.of()));
        assertEquals("数学运算", LiteracyRules.merge("非六维标签,数学运算", List.of()));  // 非六维忽略
    }

    @Test
    void 手工设置校验_仅收六维_去重排序() {
        assertEquals("数学运算,抽象概括", LiteracyRules.normalizeCsv("抽象概括, 数学运算"));
        assertEquals("数据分析", LiteracyRules.normalizeCsv("数据分析"));
        assertThrows(IllegalArgumentException.class, () -> LiteracyRules.normalizeCsv(""));
        assertThrows(IllegalArgumentException.class, () -> LiteracyRules.normalizeCsv("非六维"));
        assertThrows(IllegalArgumentException.class, () -> LiteracyRules.normalizeCsv(null));
    }
}
