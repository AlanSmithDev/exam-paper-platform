package com.examforge.paper.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 导出版面规则单测（docs/22 C6"导出默认排版即所得"，docs/09 §2.5 验收） */
class ExportRulesTest {

    @Test
    void 版面参数归一化_缺省即历史默认() {
        ExportRules.Layout l = ExportRules.normalize(null, null, null);
        assertEquals("A4", l.paper());
        assertEquals(1, l.columns());
        assertEquals("INLINE", l.answerMode());
        assertFalse(l.twoColumns());
        assertFalse(l.separated());
        assertFalse(l.hidden());
    }

    @Test
    void 版面参数归一化_未知回退与夹取() {
        // 未知纸张/栏数越界/未知答案模式 → 各自回退缺省
        assertEquals("A4", ExportRules.normalize("Letter", null, null).paper());
        assertEquals(2, ExportRules.normalize("A3", 5, null).columns());      // 上夹 2
        assertEquals(1, ExportRules.normalize("A3", 0, null).columns());      // 下夹 1
        assertEquals("INLINE", ExportRules.normalize(null, null, "xxx").answerMode());
        // 大小写与空白兼容
        assertEquals("A3", ExportRules.normalize(" a3 ", null, null).paper());
        assertEquals("NONE", ExportRules.normalize(null, null, " none ").answerMode());
        assertEquals("SEPARATED", ExportRules.normalize(null, null, "separated").answerMode());
    }

    @Test
    void 页面规则_A3双栏横向对折_其余纵向() {
        // 历史默认 @page 输出逐字不变
        assertEquals("@page { size: A4; margin: 18mm 16mm; }",
                ExportRules.pageCss(ExportRules.normalize(null, null, null)));
        assertEquals("@page { size: A3; margin: 18mm 16mm; }",
                ExportRules.pageCss(ExportRules.normalize("A3", 1, null)));
        // A3 双栏横向（对折成 A4 的试卷惯例）+ 收窄页边距
        assertEquals("@page { size: A3 landscape; margin: 12mm 10mm; }",
                ExportRules.pageCss(ExportRules.normalize("A3", 2, null)));
        assertEquals("@page { size: A4; margin: 12mm 10mm; }",
                ExportRules.pageCss(ExportRules.normalize(null, 2, null)));
    }

    @Test
    void 分栏规则_双栏生效_单栏不追加() {
        assertEquals("", ExportRules.bodyCss(ExportRules.normalize(null, null, null)));
        assertEquals("", ExportRules.bodyCss(ExportRules.normalize("A3", 1, null)));
        String a4c2 = ExportRules.bodyCss(ExportRules.normalize(null, 2, null));
        assertTrue(a4c2.contains("column-count: 2"));
        assertTrue(a4c2.contains("font-size: 10.5pt"));   // A4 双栏紧凑字号
        String a3c2 = ExportRules.bodyCss(ExportRules.normalize("A3", 2, null));
        assertTrue(a3c2.contains("column-count: 2"));
        assertTrue(a3c2.contains("font-size: 12pt"));     // A3 双栏保持正文字号
    }

    @Test
    void 文件名版面标签_不同版面互不覆盖() {
        assertEquals("a4c1i", ExportRules.fileTag(ExportRules.normalize(null, null, null)));
        assertEquals("a3c2s", ExportRules.fileTag(ExportRules.normalize("A3", 2, "SEPARATED")));
        assertEquals("a4c2n", ExportRules.fileTag(ExportRules.normalize(null, 2, "NONE")));
        assertEquals("a4c1i", ExportRules.fileTag(ExportRules.normalize(null, null, "INLINE")));
        // 四种典型版面标签两两不同
        assertEquals(4, java.util.stream.Stream.of(
                        ExportRules.normalize(null, null, null),
                        ExportRules.normalize("A3", 2, "SEPARATED"),
                        ExportRules.normalize(null, 2, "NONE"),
                        ExportRules.normalize("A3", 1, "NONE"))
                .map(ExportRules::fileTag).distinct().count());
    }

    @Test
    void 作答区归一化与CSS() {
        assertEquals("NONE", ExportRules.normalizeAnswerSpace(null));
        assertEquals("NONE", ExportRules.normalizeAnswerSpace("box"));
        assertEquals("LINE", ExportRules.normalizeAnswerSpace(" line "));
        assertEquals("BLANK", ExportRules.normalizeAnswerSpace("BLANK"));
        assertEquals("", ExportRules.answerSpaceCss("NONE"), "默认不加作答区样式（历史默认输出不变）");
        assertTrue(ExportRules.answerSpaceCss("LINE").contains("border-bottom"), "横线行作答区");
        assertTrue(ExportRules.answerSpaceCss("BLANK").contains("min-height: 30mm"), "空白框作答区");
    }

    @Test
    void 文件名标签_作答区后缀_默认不变() {
        // 不传作答区（或 NONE）→ 标签与 C6 逐字一致
        assertEquals("a4c1i", ExportRules.fileTag(ExportRules.normalize(null, null, null), null));
        assertEquals("a4c1i", ExportRules.fileTag(ExportRules.normalize(null, null, null), "NONE"));
        // 横线/空白框后缀：仍为 [a-z0-9] 且长度 ≤8（下载白名单兼容）
        assertEquals("a3c2sln", ExportRules.fileTag(ExportRules.normalize("A3", 2, "SEPARATED"), "LINE"));
        assertEquals("a4c2nbk", ExportRules.fileTag(ExportRules.normalize(null, 2, "NONE"), "BLANK"));
    }
}
