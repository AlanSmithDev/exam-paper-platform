package com.examforge.practice.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 判分单测（docs/15 §4） */
class AnswerGraderTest {

    @Test
    void 单选_精确与归一化() {
        assertEquals(Boolean.TRUE, AnswerGrader.grade(AnswerGrader.Kind.SINGLE, "C", "C"));
        assertEquals(Boolean.TRUE, AnswerGrader.grade(AnswerGrader.Kind.SINGLE, "C", " c "));
        assertEquals(Boolean.FALSE, AnswerGrader.grade(AnswerGrader.Kind.SINGLE, "C", "D"));
        assertEquals(Boolean.FALSE, AnswerGrader.grade(AnswerGrader.Kind.SINGLE, "C", ""));
    }

    @Test
    void 多选_集合相等与顺序无关() {
        assertEquals(Boolean.TRUE, AnswerGrader.grade(AnswerGrader.Kind.MULTI, "BCD", "DCB"));
        assertEquals(Boolean.TRUE, AnswerGrader.grade(AnswerGrader.Kind.MULTI, "BCD", "B D C"));
        assertEquals(Boolean.FALSE, AnswerGrader.grade(AnswerGrader.Kind.MULTI, "BCD", "BC"));
        assertEquals(Boolean.FALSE, AnswerGrader.grade(AnswerGrader.Kind.MULTI, "BCD", "ABCDE"));
    }

    @Test
    void 填空_全半角与空白归一() {
        assertEquals(Boolean.TRUE, AnswerGrader.grade(AnswerGrader.Kind.FILL, "２√３", "2√3"));
        assertEquals(Boolean.FALSE, AnswerGrader.grade(AnswerGrader.Kind.FILL, "2√3", "3√2"));
    }

    @Test
    void 判断_别名互通() {
        assertEquals(Boolean.TRUE, AnswerGrader.grade(AnswerGrader.Kind.JUDGE, "正确", "对"));
        assertEquals(Boolean.TRUE, AnswerGrader.grade(AnswerGrader.Kind.JUDGE, "T", "true"));
        assertEquals(Boolean.TRUE, AnswerGrader.grade(AnswerGrader.Kind.JUDGE, "错", "F"));
        assertEquals(Boolean.FALSE, AnswerGrader.grade(AnswerGrader.Kind.JUDGE, "对", "错"));
    }

    @Test
    void 解答题_转人工批改() {
        assertNull(AnswerGrader.grade(AnswerGrader.Kind.SOLUTION, "任何标准答案", "任何作答"));
    }
}
