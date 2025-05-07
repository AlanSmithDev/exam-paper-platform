package com.examforge.practice.logic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 扫描阅卷纯规则单测（扩展名/大小/状态机/存储路径，docs/26 §7 二阶段验收） */
class ScanRulesTest {

    @Test
    void 扩展名校验_白名单与小写归一() {
        assertEquals("jpg", ScanRules.validateExt("a.JPG"));
        assertEquals("jpeg", ScanRules.validateExt("答题卡.jpeg"));
        assertEquals("png", ScanRules.validateExt("scan.png"));
        assertEquals("pdf", ScanRules.validateExt("s.t.pdf"));
        assertThrows(IllegalArgumentException.class, () -> ScanRules.validateExt("noext"));
        assertThrows(IllegalArgumentException.class, () -> ScanRules.validateExt("a.gif"));
        assertThrows(IllegalArgumentException.class, () -> ScanRules.validateExt(""));
        assertThrows(IllegalArgumentException.class, () -> ScanRules.validateExt(null));
    }

    @Test
    void 大小校验_1B到10MB() {
        assertDoesNotThrow(() -> ScanRules.validateSize(1));
        assertDoesNotThrow(() -> ScanRules.validateSize(ScanRules.MAX_SIZE_BYTES));
        assertThrows(IllegalArgumentException.class, () -> ScanRules.validateSize(0));
        assertThrows(IllegalArgumentException.class, () -> ScanRules.validateSize(ScanRules.MAX_SIZE_BYTES + 1));
    }

    @Test
    void 状态机_单向不可跳级() {
        assertTrue(ScanRules.canTransition("UPLOADED", "RECOGNIZED"));
        assertTrue(ScanRules.canTransition("RECOGNIZED", "IMPORTED"));
        assertFalse(ScanRules.canTransition("UPLOADED", "IMPORTED"));   // 不可跳级
        assertFalse(ScanRules.canTransition("IMPORTED", "UPLOADED"));   // 不可回退
        assertFalse(ScanRules.canTransition("RECOGNIZED", "UPLOADED"));
        assertThrows(IllegalStateException.class, () -> ScanRules.mustTransition("UPLOADED", "IMPORTED"));
    }

    @Test
    void 存储路径_按作业学生与时间戳分层() {
        assertEquals("scans/12/345/1727500000000.jpg",
                ScanRules.storagePath(12, 345, 1727500000000L, "jpg"));
    }

    @Test
    void 识别结果解析_对象包裹与裸数组_容错与非法() {
        assertEquals(List.of(Map.of("questionId", 11L, "answer", "A")),
                ScanRules.parseAnswers("{\"recognized\":true,\"answers\":[{\"questionId\":11,\"answer\":\"A\"}]}"));
        assertEquals(2, ScanRules.parseAnswers(
                "[{\"questionId\":1,\"answer\":\"对\"},{\"questionId\":2,\"answer\":\"\"}]").size());
        // 缺 answer 的项跳过，有效项保留
        assertEquals(1, ScanRules.parseAnswers(
                "[{\"questionId\":1},{\"other\":9},{\"questionId\":2,\"answer\":\"B\"}]").size());
        assertThrows(IllegalArgumentException.class, () -> ScanRules.parseAnswers(null));
        assertThrows(IllegalArgumentException.class, () -> ScanRules.parseAnswers("  "));
        assertThrows(IllegalArgumentException.class, () -> ScanRules.parseAnswers("not-json"));
        assertThrows(IllegalArgumentException.class, () -> ScanRules.parseAnswers("{\"recognized\":false,\"reason\":\"模糊\"}"));
        assertThrows(IllegalArgumentException.class, () -> ScanRules.parseAnswers("[]"));
    }
}
