package com.examforge.practice.logic;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 作业域纯逻辑单测（状态机/超时/成绩/班级知识点报告，docs/26 F-XKW-12 验收） */
class AssignmentRulesTest {

    @Test
    void 作业状态机_合法跃迁与非法跃迁() {
        assertTrue(AssignmentRules.canTransition(AssignmentRules.DRAFT, AssignmentRules.PUBLISHED));
        assertTrue(AssignmentRules.canTransition(AssignmentRules.DRAFT, AssignmentRules.CLOSED));      // 草稿作废
        assertTrue(AssignmentRules.canTransition(AssignmentRules.PUBLISHED, AssignmentRules.CLOSED));
        assertFalse(AssignmentRules.canTransition(AssignmentRules.PUBLISHED, AssignmentRules.PUBLISHED));
        assertFalse(AssignmentRules.canTransition(AssignmentRules.CLOSED, AssignmentRules.PUBLISHED)); // 关闭不可复活
        assertFalse(AssignmentRules.canTransition(AssignmentRules.CLOSED, AssignmentRules.CLOSED));
        assertThrows(IllegalStateException.class,
                () -> AssignmentRules.mustTransition(AssignmentRules.CLOSED, AssignmentRules.PUBLISHED));
    }

    @Test
    void 超时判定_空截止不限时() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 28, 20, 0);
        assertTrue(AssignmentRules.late(now.minusHours(1), now));    // 截止已过
        assertFalse(AssignmentRules.late(now.plusHours(1), now));    // 未到截止
        assertFalse(AssignmentRules.late(now, now));                 // 恰好等于截止不算超时
        assertFalse(AssignmentRules.late(null, now));                // 不限时
        assertFalse(AssignmentRules.late(now, null));
    }

    @Test
    void 成绩计算_客观题立即出_解答题待批不计入() {
        // 10 题：客观 8 题对 6，解答 2 题待批 → 正确率 = 6/8 = 75%
        AssignmentRules.ScoreCalc c = AssignmentRules.calcScore(10, 6, 2);
        assertEquals(75.0, c.correctRatePct());
        assertEquals(8, c.gradedCount());
        assertEquals(2, c.pendingManual());
        assertTrue(c.needsManual());

        // 全部待批（纯解答卷）→ 0 分且标记需人工
        AssignmentRules.ScoreCalc all = AssignmentRules.calcScore(3, 0, 3);
        assertEquals(0.0, all.correctRatePct());
        assertTrue(all.needsManual());

        // 纯客观卷 4 对 3 → 75%
        AssignmentRules.ScoreCalc obj = AssignmentRules.calcScore(4, 3, 0);
        assertEquals(75.0, obj.correctRatePct());
        assertFalse(obj.needsManual());
    }

    @Test
    void 成绩计算_四舍五入到两位() {
        AssignmentRules.ScoreCalc c = AssignmentRules.calcScore(3, 1, 0);   // 33.333 → 33.33
        assertEquals(33.33, c.correctRatePct());
        AssignmentRules.ScoreCalc c2 = AssignmentRules.calcScore(6, 5, 0);  // 83.333 → 83.33
        assertEquals(83.33, c2.correctRatePct());
    }

    @Test
    void 班级报告_薄弱知识点按错误数降序() {
        List<AssignmentRules.KpRow> rows = List.of(
                new AssignmentRules.KpRow("集合,函数", true),
                new AssignmentRules.KpRow("函数", false),
                new AssignmentRules.KpRow(" 函数 ，数列", false),
                new AssignmentRules.KpRow("复数", true),
                new AssignmentRules.KpRow(null, false),        // 无标签忽略
                new AssignmentRules.KpRow("  ", true)
        );
        List<java.util.Map<String, Object>> report = AssignmentRules.kpAggregate(rows);
        // 函数 3 题 2 错 → 第一；集合 1 题 0 错；数列 1 题 1 错；复数 1 题 0 错
        assertEquals("函数", report.get(0).get("kp"));
        assertEquals(3, report.get(0).get("total"));
        assertEquals(2, report.get(0).get("wrong"));
        assertEquals(4, report.size());
        // 数列（1错）排在 集合/复数（0错）之前；0错的两个按 Unicode 码点序：复数(U+590D) < 集合(U+96C6)
        // 顺序：函数(2错) → 数列(1错) → 复数(0错) → 集合(0错)
        assertEquals(List.of("数列", "复数", "集合"),
                report.stream().skip(1).map(m -> (String) m.get("kp")).toList());
        assertEquals(0.0, report.get(1).get("correctRatePct"));   // 数列 0%
        assertEquals(100.0, report.get(2).get("correctRatePct")); // 复数 100%
    }

    @Test
    void 班级报告_空输入() {
        assertTrue(AssignmentRules.kpAggregate(List.of()).isEmpty());
    }
}
