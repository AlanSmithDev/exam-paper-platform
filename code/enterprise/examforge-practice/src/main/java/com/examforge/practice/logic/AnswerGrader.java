package com.examforge.practice.logic;

import java.util.Arrays;
import java.util.TreeSet;

/** 纯逻辑：客观题自动判分（docs/15 PR-2），单测覆盖 */
public class AnswerGrader {

    public enum Kind { SINGLE, MULTI, FILL, JUDGE, SOLUTION }

    /** 题型名 → 判分类型（练习/作业两域共用映射） */
    public static Kind kindOf(String questionType) {
        return switch (questionType == null ? "" : questionType) {
            case "单选题" -> Kind.SINGLE;
            case "多选题" -> Kind.MULTI;
            case "填空题" -> Kind.FILL;
            case "判断题" -> Kind.JUDGE;
            default -> Kind.SOLUTION;
        };
    }

    /** 返回：true 对 / false 错 / null 待人工批改（解答题） */
    public static Boolean grade(Kind kind, String standard, String userAnswer) {
        if (kind == Kind.SOLUTION) return null;                        // 解答题人工批改
        String s = normalize(standard), u = normalize(userAnswer);
        if (u.isEmpty()) return false;
        return switch (kind) {
            case SINGLE, JUDGE -> s.equalsIgnoreCase(u) || judgeAlias(s, u);
            case MULTI -> new TreeSet<>(Arrays.asList(s.split(""))).equals(new TreeSet<>(Arrays.asList(u.split(""))));
            case FILL -> s.equals(u);
            default -> false;
        };
    }

    /** 判断题别名：对/正确/T/right → true */
    private static boolean judgeAlias(String s, String u) {
        boolean std = s.matches("(?i)(对|正确|T|true|正确答案)");
        boolean usr = u.matches("(?i)(对|正确|T|true)");
        boolean stdF = s.matches("(?i)(错|错误|F|false)");
        boolean usrF = u.matches("(?i)(错|错误|F|false)");
        return (std && usr) || (stdF && usrF);
    }

    /** 归一化：去空白、全角转半角、统一大写 */
    static String normalize(String v) {
        if (v == null) return "";
        StringBuilder b = new StringBuilder();
        for (char c : v.trim().toCharArray()) {
            if (Character.isWhitespace(c)) continue;
            if (c >= 'Ａ' && c <= 'Ｚ') c = (char) (c - 'Ａ' + 'A');
            else if (c >= 'ａ' && c <= 'ｚ') c = (char) (c - 'ａ' + 'A');
            else if (c >= '０' && c <= '９') c = (char) (c - '０' + '0');
            b.append(Character.toUpperCase(c));
        }
        return b.toString();
    }
}
