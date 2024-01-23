package com.examforge.paper.logic;

/** 导出版面纯规则（docs/22 C6"导出默认排版即所得"，docs/09 §2.5；可脱离 Spring 单测） */
public final class ExportRules {

    public static final String PAPER_A4 = "A4";
    public static final String PAPER_A3 = "A3";

    /** 答案模式：INLINE 题后随卷（历史默认）/ SEPARATED 末尾独立答案页 / NONE 不含答案（学生卷） */
    public static final String ANS_INLINE = "INLINE";
    public static final String ANS_SEPARATED = "SEPARATED";
    public static final String ANS_NONE = "NONE";

    /** 导出版面：纸张 A4/A3 + 栏数 1/2 + 答案模式 */
    public record Layout(String paper, int columns, String answerMode) {
        public boolean twoColumns() { return columns == 2; }
        public boolean separated() { return ANS_SEPARATED.equals(answerMode); }
        public boolean hidden() { return ANS_NONE.equals(answerMode); }
    }

    private ExportRules() { }

    /**
     * 版面参数归一化：纸张大小写兼容、未知回退 A4；栏数夹取 [1,2]；答案模式未知回退 INLINE。
     * 三参全空即历史默认版面，存量调用行为不变。
     */
    public static Layout normalize(String paper, Integer columns, String answerMode) {
        String p = paper == null ? "" : paper.trim().toUpperCase();
        String normPaper = PAPER_A3.equals(p) ? PAPER_A3 : PAPER_A4;
        int normCols = columns == null ? 1 : Math.max(1, Math.min(columns, 2));
        String mode = switch (answerMode == null ? "" : answerMode.trim().toUpperCase()) {
            case ANS_SEPARATED -> ANS_SEPARATED;
            case ANS_NONE -> ANS_NONE;
            default -> ANS_INLINE;
        };
        return new Layout(normPaper, normCols, mode);
    }

    /** @page 规则：A3 双栏横向对折成 A4 的标准试卷惯例，其余纵向；双栏收窄页边距 */
    public static String pageCss(Layout l) {
        boolean a3 = PAPER_A3.equals(l.paper());
        String size = a3 && l.twoColumns() ? "A3 landscape" : l.paper();
        String margin = l.twoColumns() ? "12mm 10mm" : "18mm 16mm";
        return "@page { size: " + size + "; margin: " + margin + "; }";
    }

    /** 分栏与字号：双栏 column-count:2（A4 紧凑 10.5pt / A3 12pt），单栏不追加规则保持默认 */
    public static String bodyCss(Layout l) {
        if (!l.twoColumns()) return "";
        boolean a3 = PAPER_A3.equals(l.paper());
        return "body { column-count: 2; column-gap: " + (a3 ? "12mm" : "8mm")
                + "; font-size: " + (a3 ? "12pt" : "10.5pt") + "; }";
    }

    /** 产物文件名版面标签（paper-{id}-{hash8}-{tag}）：不同版面产物不互相覆盖，如 a4c1i / a3c2s / a4c2n */
    public static String fileTag(Layout l) {
        String m = l.separated() ? "s" : l.hidden() ? "n" : "i";
        return l.paper().toLowerCase() + "c" + l.columns() + m;
    }
}
