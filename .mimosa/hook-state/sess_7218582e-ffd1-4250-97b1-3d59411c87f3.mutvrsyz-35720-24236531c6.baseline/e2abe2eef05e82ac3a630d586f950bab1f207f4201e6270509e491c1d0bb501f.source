package com.examforge.paper.logic;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 试卷模板纯规则单测（docs/25 TJ-80/TJ-22 验收） */
class TemplateRulesTest {

    @Test
    void 模板名归一化_去空白夹取与缺省() {
        assertEquals("我的期末卷", TemplateRules.normalizeName(" 我的期末卷 "));
        assertEquals(50, TemplateRules.normalizeName("长".repeat(80)).length(), "超长截断到 50");
        assertEquals("未命名模板", TemplateRules.normalizeName(""));
        assertEquals("未命名模板", TemplateRules.normalizeName(null));
    }

    @Test
    void 快照校验_空值重复与分值范围() {
        assertNull(TemplateRules.validateSnapshot(List.of(
                new TemplateRules.SnapshotItem(1, 5), new TemplateRules.SnapshotItem(2, 10))), "合法快照通过");
        assertTrue(TemplateRules.validateSnapshot(List.of()).contains("不能为空"));
        assertTrue(TemplateRules.validateSnapshot(null).contains("不能为空"));
        assertTrue(TemplateRules.validateSnapshot(List.of(
                new TemplateRules.SnapshotItem(1, 5), new TemplateRules.SnapshotItem(1, 5))).contains("重复"));
        assertTrue(TemplateRules.validateSnapshot(List.of(new TemplateRules.SnapshotItem(1, 0))).contains("1~100"));
        assertTrue(TemplateRules.validateSnapshot(List.of(new TemplateRules.SnapshotItem(1, 101))).contains("1~100"));
    }

    @Test
    void 快照总分与应用标题() {
        assertEquals(15, TemplateRules.totalScore(List.of(
                new TemplateRules.SnapshotItem(1, 5), new TemplateRules.SnapshotItem(2, 10))));
        LocalDateTime now = LocalDateTime.of(2026, 9, 30, 10, 0);
        assertEquals("我的期末卷·副本20260930", TemplateRules.appliedTitle(" 我的期末卷 ", now));
        assertEquals("未命名模板·副本20260930", TemplateRules.appliedTitle(null, now));
    }

    @Test
    void 模板数量上限() {
        assertTrue(TemplateRules.canCreate(0));
        assertTrue(TemplateRules.canCreate(49));
        assertFalse(TemplateRules.canCreate(50));
        assertFalse(TemplateRules.canCreate(60));
    }
}
