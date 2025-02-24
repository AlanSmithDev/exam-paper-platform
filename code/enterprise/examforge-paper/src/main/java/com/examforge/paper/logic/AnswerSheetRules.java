package com.examforge.paper.logic;

import java.util.ArrayList;
import java.util.List;

/** 答题卡版面纯规则（e 卷通二阶段，docs/26 §7"答题卡一键生成"；可脱离 Spring 单测） */
public final class AnswerSheetRules {

    /** 客观题网格每行题数（A-D 四选项惯例；A-E 也按 5/行容纳） */
    public static final int OBJECTIVE_PER_ROW = 5;

    /** 考号涂格位数（学科网惯例 8~10 位，取 10） */
    public static final int ID_BOX_COUNT = 10;

    private AnswerSheetRules() { }

    /** 判断题固定 A/B（图例：A=对，B=错） */
    public static List<String> judgeLetters() { return List.of("A", "B"); }

    /** 客观题气泡字母：按选项数夹取 [2,6]，无选项信息默认 A-D */
    public static List<String> objectiveLetters(int optionCount) {
        int n = Math.max(2, Math.min(optionCount <= 0 ? 4 : optionCount, 6));
        List<String> letters = new ArrayList<>(n);
        for (int i = 0; i < n; i++) letters.add(String.valueOf((char) ('A' + i)));
        return letters;
    }

    /** 客观题（涂卡）：单选/多选/判断 */
    public static boolean objectiveType(String type) {
        return "单选题".equals(type) || "多选题".equals(type) || "判断题".equals(type);
    }

    /** 填空题（写卡：括号线） */
    public static boolean fillType(String type) {
        return type != null && type.contains("填空");
    }

    /** 解答题作答区高度（mm）：分值×8mm，夹取 [30,150] */
    public static int answerAreaHeightMm(Integer score) {
        int s = score == null ? 5 : score;
        return Math.max(30, Math.min(150, s * 8));
    }

    /** 题号按每行 N 个分页（客观题网格行） */
    public static <T> List<List<T>> paginate(List<T> items, int perRow) {
        List<List<T>> rows = new ArrayList<>();
        int cap = Math.max(1, perRow);
        for (int i = 0; i < items.size(); i += cap) {
            rows.add(items.subList(i, Math.min(i + cap, items.size())));
        }
        return rows;
    }
}
