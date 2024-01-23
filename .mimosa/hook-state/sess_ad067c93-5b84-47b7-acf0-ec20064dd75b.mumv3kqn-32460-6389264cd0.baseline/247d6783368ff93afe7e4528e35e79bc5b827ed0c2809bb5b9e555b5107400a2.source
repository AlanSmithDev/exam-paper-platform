package com.examforge.paper.service;

import com.examforge.api.dto.QuestionSummaryDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 智能组卷引擎（规则求解）：
 * 输入蓝图 {subjectId, structure:[{type,count,score}], difficultyTarget, excludeIds}
 * 求解约束：①各题型数量达标 ②全卷无重复 ③难度围绕目标难度向两侧扩展
 * 输出：试卷题目（由易到难）+ 总分 + 难度拟合度
 * 单测覆盖：PaperGenerateServiceTest
 */
@Slf4j
@Component
public class PaperGenerateEngine {

    public record Picked(Long id, String type, Integer difficulty, Double coefficient, String stem, int score) { }

    public record Outcome(List<Picked> questions, int totalScore, double difficultyFit, List<String> warnings) { }

    private final QuestionFetcher fetcher;

    public PaperGenerateEngine(QuestionFetcher fetcher) {
        this.fetcher = fetcher;
    }

    public interface QuestionFetcher {
        List<QuestionSummaryDTO> byType(Long subjectId, String type, Integer difficulty, int limit);
    }

    public Outcome generate(Long subjectId, List<Map<String, Object>> structure,
                            Integer difficultyTarget, List<Long> excludeIds) {
        Set<Long> used = new HashSet<>();
        if (excludeIds != null) used.addAll(excludeIds);
        int target = difficultyTarget == null ? 3 : Math.max(1, Math.min(5, difficultyTarget));
        List<Picked> pickedList = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int totalScore = 0;

        for (Map<String, Object> sec : structure) {
            String type = String.valueOf(sec.getOrDefault("type", ""));
            int want = clamp(sec.get("count"), 0, 30);
            int score = clamp(sec.get("score"), 1, 100);
            int got = 0;

            for (int delta = 0; delta <= 4 && got < want; delta++) {
                for (int d : new int[]{target - delta, target + delta}) {
                    if (d < 1 || d > 5 || got >= want) continue;
                    for (QuestionSummaryDTO q : fetcher.byType(subjectId, type, d, want * 3)) {
                        if (got >= want) break;
                        if (used.contains(q.getId())) continue;
                        used.add(q.getId());
                        pickedList.add(new Picked(q.getId(), q.getType(), q.getDifficulty(), q.getCoefficient(), q.getStem(), score));
                        got++;
                        totalScore += score;
                    }
                }
            }
            if (got < want) {
                warnings.add("题型「" + type + "」题量不足：需要 " + want + "，实际 " + got + "（可放宽难度或补充题库）");
            }
        }

        pickedList.sort(Comparator.comparingInt(Picked::difficulty));
        double fit = pickedList.isEmpty() ? 0
                : Math.round(100 - pickedList.stream()
                        .mapToInt(p -> Math.abs(p.difficulty() - target) * 25)
                        .average().orElse(25)) / 100.0;
        return new Outcome(pickedList, totalScore, fit, warnings);
    }

    private int clamp(Object v, int min, int max) {
        int n;
        try { n = Integer.parseInt(String.valueOf(v)); } catch (Exception e) { n = min; }
        return Math.max(min, Math.min(max, n));
    }
}
