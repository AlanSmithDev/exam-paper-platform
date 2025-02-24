package com.examforge.paper.logic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 答题卡版面规则单测（docs/26 F-XKW-12 二阶段验收） */
class AnswerSheetRulesTest {

    @Test
    void 气泡字母_按选项数夹取() {
        assertEquals(List.of("A", "B", "C", "D"), AnswerSheetRules.objectiveLetters(4));
        assertEquals(List.of("A", "B", "C", "D", "E"), AnswerSheetRules.objectiveLetters(5));
        assertEquals(List.of("A", "B", "C", "D", "E", "F"), AnswerSheetRules.objectiveLetters(6));
        assertEquals(List.of("A", "B", "C", "D", "E", "F"), AnswerSheetRules.objectiveLetters(9));   // 上夹 6
        assertEquals(List.of("A", "B", "C", "D"), AnswerSheetRules.objectiveLetters(0));             // 缺省 A-D
        assertEquals(List.of("A", "B"), AnswerSheetRules.objectiveLetters(2));
    }

    @Test
    void 判断题固定AB() {
        assertEquals(List.of("A", "B"), AnswerSheetRules.judgeLetters());
    }

    @Test
    void 题型分区判定() {
        assertTrue(AnswerSheetRules.objectiveType("单选题"));
        assertTrue(AnswerSheetRules.objectiveType("多选题"));
        assertTrue(AnswerSheetRules.objectiveType("判断题"));
        assertFalse(AnswerSheetRules.objectiveType("填空题"));
        assertFalse(AnswerSheetRules.objectiveType("解答题"));
        assertTrue(AnswerSheetRules.fillType("填空题"));
        assertTrue(AnswerSheetRules.fillType("填空题-双空题"));
        assertFalse(AnswerSheetRules.fillType("解答题"));
        assertFalse(AnswerSheetRules.fillType(null));
    }

    @Test
    void 作答区高度_分值线性_上下夹取() {
        assertEquals(30, AnswerSheetRules.answerAreaHeightMm(2));     // 下夹 30mm
        assertEquals(40, AnswerSheetRules.answerAreaHeightMm(5));
        assertEquals(80, AnswerSheetRules.answerAreaHeightMm(10));
        assertEquals(150, AnswerSheetRules.answerAreaHeightMm(20));   // 上夹 150mm
        assertEquals(150, AnswerSheetRules.answerAreaHeightMm(50));
        assertEquals(40, AnswerSheetRules.answerAreaHeightMm(null));  // 缺省 5 分
    }

    @Test
    void 题号分页_每行5个() {
        List<List<Integer>> rows = AnswerSheetRules.paginate(List.of(1, 2, 3, 4, 5, 6, 7, 11, 12), 5);
        assertEquals(2, rows.size());
        assertEquals(List.of(1, 2, 3, 4, 5), rows.get(0));
        assertEquals(List.of(6, 7, 11, 12), rows.get(1));
        assertTrue(AnswerSheetRules.paginate(List.of(), 5).isEmpty());
        assertEquals(1, AnswerSheetRules.paginate(List.of(9), 5).size());
    }

    @Test
    void 每行容量非法入参防护() {
        List<List<Integer>> rows = AnswerSheetRules.paginate(List.of(1, 2, 3), 0);   // 容量夹 1
        assertEquals(3, rows.size());
        assertEquals(List.of(1), rows.get(0));
    }
}
