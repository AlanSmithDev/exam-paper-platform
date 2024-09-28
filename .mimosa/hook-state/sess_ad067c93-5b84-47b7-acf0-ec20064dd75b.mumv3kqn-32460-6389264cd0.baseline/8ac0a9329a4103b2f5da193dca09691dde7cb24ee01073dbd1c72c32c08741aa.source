package com.examforge.paper.logic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 组卷编辑纯规则：整卷插题/移除重排/默认分值/考查范围聚合（docs/26 F-XKW-05，可脱离 Spring 单测） */
public final class PaperRules {

    private PaperRules() { }

    /** 插入/追加默认分值（学科网题型惯例；蓝图指定分值优先于本表） */
    public static int defaultScore(String type) {
        return switch (type == null ? "" : type) {
            case "单选题", "多选题", "判断题" -> 3;
            case "填空题" -> 5;
            case "解答题" -> 12;
            default -> 5;
        };
    }

    /**
     * 整卷插题：position 为 1 起始的插入位（1=插到最前，size+1=追加）。
     * 返回插入后的有序题目 ID 列表；非法位置抛 IllegalArgumentException。
     */
    public static List<Long> applyInsert(List<Long> orderedIds, Long newQuestionId, int position) {
        int size = orderedIds.size();
        if (position < 1 || position > size + 1) {
            throw new IllegalArgumentException("插入位置须在 1~" + (size + 1) + " 之间");
        }
        List<Long> result = new ArrayList<>(size + 1);
        result.addAll(orderedIds);
        result.add(position - 1, newQuestionId);
        return result;
    }

    /** 整卷移除：返回移除后的有序列表；题目不在卷中抛 IllegalArgumentException */
    public static List<Long> applyRemove(List<Long> orderedIds, Long questionId) {
        if (!orderedIds.contains(questionId)) {
            throw new IllegalArgumentException("该题不在本卷中");
        }
        List<Long> result = new ArrayList<>(orderedIds);
        result.remove(questionId);
        return result;
    }

    /** 重复入卷校验 */
    public static boolean duplicated(List<Long> orderedIds, Long questionId) {
        return orderedIds.contains(questionId);
    }

    /**
     * 考查范围聚合（学科网试卷详情"考查范围"条同构）：入参为每题 kpNames（逗号分隔），
     * 输出按出现题数降序、同频按名称排列的知识点与题数；空串/空白过滤。
     */
    public static List<Map<String, Object>> scopeAggregation(List<String> kpNamesCsvList) {
        Map<String, Integer> counter = new LinkedHashMap<>();
        for (String csv : kpNamesCsvList) {
            if (csv == null || csv.isBlank()) continue;
            for (String raw : csv.split("[,，、]")) {
                String kp = raw.trim();
                if (kp.isEmpty()) continue;
                counter.merge(kp, 1, Integer::sum);
            }
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counter.entrySet());
        entries.sort((a, b) -> {
            int byCount = Integer.compare(b.getValue(), a.getValue());
            return byCount != 0 ? byCount : a.getKey().compareTo(b.getKey());
        });
        List<Map<String, Object>> result = new ArrayList<>(entries.size());
        for (Map.Entry<String, Integer> e : entries) {
            result.add(Map.of("kp", e.getKey(), "count", e.getValue()));
        }
        return result;
    }
}
