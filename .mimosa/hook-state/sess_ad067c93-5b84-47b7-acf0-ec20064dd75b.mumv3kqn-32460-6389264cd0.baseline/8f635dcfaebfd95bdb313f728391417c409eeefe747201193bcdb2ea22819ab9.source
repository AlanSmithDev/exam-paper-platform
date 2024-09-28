package com.examforge.question.logic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 素养标签纯规则（T-26a，docs/27 §6：按知识点映射初打 + 运营单题覆盖；可脱离 Spring 单测） */
public final class LiteracyRules {

    /** 素养六维（docs/27 §6，数学学科口径，固定顺序） */
    public static final List<String> SIX = List.of("数学运算", "逻辑推理", "直观想象", "数学建模", "数据分析", "抽象概括");

    /** 知识点关键词 → 素养映射（初打口径：命中即打；运营可单题覆盖） */
    private static final Map<String, String> KP_KEYWORDS = Map.ofEntries(
            Map.entry("函数", "数学运算"), Map.entry("运算", "数学运算"), Map.entry("数列", "数学运算"),
            Map.entry("不等式", "数学运算"), Map.entry("求值", "数学运算"),
            Map.entry("推理", "逻辑推理"), Map.entry("逻辑", "逻辑推理"), Map.entry("证明", "逻辑推理"),
            Map.entry("命题", "逻辑推理"),
            Map.entry("几何", "直观想象"), Map.entry("向量", "直观想象"), Map.entry("立体", "直观想象"),
            Map.entry("图像", "直观想象"), Map.entry("图象", "直观想象"), Map.entry("直观", "直观想象"),
            Map.entry("建模", "数学建模"), Map.entry("应用", "数学建模"), Map.entry("实际", "数学建模"),
            Map.entry("最值", "数学建模"),
            Map.entry("统计", "数据分析"), Map.entry("概率", "数据分析"), Map.entry("数据", "数据分析"),
            Map.entry("随机", "数据分析"),
            Map.entry("抽象", "抽象概括"), Map.entry("概括", "抽象概括"), Map.entry("概念", "抽象概括"),
            Map.entry("定义", "抽象概括"), Map.entry("性质", "抽象概括"));

    private LiteracyRules() { }

    /** 按 kpNames（CSV）映射素养：知识点命中关键词即归入对应维度，未命中不猜；按六维固定顺序去重 */
    public static List<String> autoTag(String kpNamesCsv) {
        Set<String> hits = new LinkedHashSet<>();
        for (String kp : split(kpNamesCsv)) {
            for (Map.Entry<String, String> e : KP_KEYWORDS.entrySet()) {
                if (kp.contains(e.getKey())) hits.add(e.getValue());
            }
        }
        return order(hits);
    }

    /** 合并已有素养（CSV）与自动打标集合，按六维顺序去重输出 CSV */
    public static String merge(String existingCsv, List<String> autoTags) {
        Set<String> all = new LinkedHashSet<>(valid(existingCsv));
        all.addAll(valid(String.join(",", autoTags == null ? List.<String>of() : autoTags)));
        List<String> ordered = order(all);
        return ordered.isEmpty() ? "" : String.join(",", ordered);
    }

    /** 手工设置入口校验：标签须属素养六维，返回去重排序后的 CSV；全非法抛 IllegalArgumentException */
    public static String normalizeCsv(String csv) {
        List<String> tags = valid(csv);
        if (tags.isEmpty()) throw new IllegalArgumentException("素养标签须属于六维：" + String.join("/", SIX));
        return String.join(",", order(new LinkedHashSet<>(tags)));
    }

    private static List<String> valid(String csv) {
        List<String> out = new ArrayList<>();
        if (csv != null) for (String t : csv.split("[,，]")) {
            if (!t.isBlank() && SIX.contains(t.trim())) out.add(t.trim());
        }
        return out;
    }

    private static List<String> split(String csv) {
        List<String> out = new ArrayList<>();
        if (csv != null) for (String s : csv.split("[,，]")) {
            if (!s.isBlank()) out.add(s.trim());
        }
        return out;
    }

    private static List<String> order(Set<String> tags) {
        List<String> out = new ArrayList<>();
        for (String s : SIX) if (tags.contains(s)) out.add(s);
        for (String t : tags) if (!out.contains(t)) out.add(t);
        return out;
    }
}
