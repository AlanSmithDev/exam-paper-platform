package com.examforge.practice.logic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 作业域纯规则：状态机/提交判定/成绩计算/班级报告聚合（docs/26 F-XKW-12，可脱离 Spring 单测） */
public final class AssignmentRules {

    public static final int DRAFT = 0;
    public static final int PUBLISHED = 1;
    public static final int CLOSED = 2;

    public static final int ST_ASSIGNED = 0;   // 学生：待完成
    public static final int ST_SUBMITTED = 1;  // 已提交
    public static final int ST_GRADED = 2;     // 已批改

    private AssignmentRules() { }

    /** 作业状态机：草稿→发布→关闭；草稿可作废（直接关闭） */
    public static boolean canTransition(int from, int to) {
        return switch (from) {
            case DRAFT -> to == PUBLISHED || to == CLOSED;
            case PUBLISHED -> to == CLOSED;
            default -> false;
        };
    }

    public static void mustTransition(int from, int to) {
        if (!canTransition(from, to)) throw new IllegalStateException("非法作业状态跃迁: " + from + " -> " + to);
    }

    /** 是否超时提交（deadline 为空视为不限时） */
    public static boolean late(java.time.LocalDateTime deadline, java.time.LocalDateTime now) {
        return deadline != null && now != null && now.isAfter(deadline);
    }

    /** 成绩计算：客观题自动判分 + 解答题待批改；正确率=客观题正确数/已判题数（解答题未批不计入） */
    public static ScoreCalc calcScore(int total, int correctObjective, int pendingManual) {
        int graded = total - pendingManual;
        double rate = graded <= 0 ? 0 : Math.round(correctObjective * 10000.0 / graded) / 100.0;
        return new ScoreCalc(rate, graded, pendingManual);
    }

    public record ScoreCalc(double correctRatePct, int gradedCount, int pendingManual) {
        public boolean needsManual() { return pendingManual > 0; }
    }

    /**
     * 班级知识点报告：按"错题优先"聚合（教师视角：薄弱知识点=错误数多的）。
     * 输入：每条作答的（题目知识点CSV, 是否正确）；输出按错误数降序、同频按名称。
     */
    public static List<Map<String, Object>> kpAggregate(List<KpRow> rows) {
        Map<String, int[]> agg = new LinkedHashMap<>();   // [total, wrong]
        for (KpRow r : rows) {
            String csv = r.kpCsv() == null ? "" : r.kpCsv();
            if (csv.isBlank()) continue;
            for (String raw : csv.split("[,，、]")) {
                String kp = raw.trim();
                if (kp.isEmpty()) continue;
                int[] arr = agg.computeIfAbsent(kp, k -> new int[2]);
                arr[0]++;
                if (!r.correct()) arr[1]++;
            }
        }
        List<Map.Entry<String, int[]>> entries = new ArrayList<>(agg.entrySet());
        entries.sort((a, b) -> {
            int byWrong = Integer.compare(b.getValue()[1], a.getValue()[1]);
            return byWrong != 0 ? byWrong : a.getKey().compareTo(b.getKey());
        });
        List<Map<String, Object>> out = new ArrayList<>(entries.size());
        for (Map.Entry<String, int[]> e : entries) {
            int total = e.getValue()[0], wrong = e.getValue()[1];
            double rate = total == 0 ? 0 : Math.round((total - wrong) * 10000.0 / total) / 100.0;
            out.add(Map.of("kp", e.getKey(), "total", total, "wrong", wrong, "correctRatePct", rate));
        }
        return out;
    }

    public record KpRow(String kpCsv, boolean correct) { }
}
