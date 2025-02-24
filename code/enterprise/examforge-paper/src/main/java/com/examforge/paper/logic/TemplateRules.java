package com.examforge.paper.logic;

import java.time.LocalDateTime;
import java.util.List;

/** 试卷模板纯规则（docs/25 TJ-80/TJ-22"存为模版/模板选题"；可脱离 Spring 单测） */
public final class TemplateRules {

    /** 模板名长度夹取范围 */
    public static final int NAME_MIN_LEN = 1;
    public static final int NAME_MAX_LEN = 50;
    /** 单题分值范围（与组卷蓝图口径一致） */
    public static final int SCORE_MIN = 1;
    public static final int SCORE_MAX = 100;
    /** 我的模板数量上限（防滥用；超出时最旧的可删除腾位） */
    public static final int MAX_TEMPLATES_PER_USER = 50;

    /** 模板题目快照条目 */
    public record SnapshotItem(long questionId, int score) { }

    private TemplateRules() { }

    /** 模板名归一化：去首尾空白 + 长度夹取；空名回退"未命名模板" */
    public static String normalizeName(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) return "未命名模板";
        return n.length() > NAME_MAX_LEN ? n.substring(0, NAME_MAX_LEN) : n;
    }

    /** 快照合法性：题目非空、无重复、分值在 [1,100]；非法返回错误文案，null 表示通过 */
    public static String validateSnapshot(List<SnapshotItem> items) {
        if (items == null || items.isEmpty()) return "模板题目不能为空";
        long distinct = items.stream().map(SnapshotItem::questionId).distinct().count();
        if (distinct != items.size()) return "模板内题目重复";
        boolean badScore = items.stream().anyMatch(i -> i.score() < SCORE_MIN || i.score() > SCORE_MAX);
        if (badScore) return "分值需在 " + SCORE_MIN + "~" + SCORE_MAX + " 之间";
        return null;
    }

    /** 快照总分 */
    public static int totalScore(List<SnapshotItem> items) {
        return items == null ? 0 : items.stream().mapToInt(SnapshotItem::score).sum();
    }

    /** 从模板应用出新卷的缺省标题：模板名 + "·副本" + 日期后缀（同日多次应用可区分） */
    public static String appliedTitle(String templateName, LocalDateTime now) {
        String n = normalizeName(templateName);
        return n + "·副本" + (now == null ? "" : now.toLocalDate().toString().replace("-", ""));
    }

    /** 是否还能创建模板（超出上限需先删除） */
    public static boolean canCreate(long currentCount) {
        return currentCount < MAX_TEMPLATES_PER_USER;
    }
}
