package com.examforge.paper.service;

import com.examforge.api.dto.QuestionSummaryDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** 组卷引擎单测：数量达标 / 无重复 / 难度曲线 / 题量不足告警 */
class PaperGenerateEngineTest {

    private QuestionSummaryDTO q(long id, String type, int diff) {
        QuestionSummaryDTO d = new QuestionSummaryDTO();
        d.setId(id); d.setType(type); d.setDifficulty(diff); d.setCoefficient(1.0 - diff * 0.15);
        d.setStem("题目 " + id);
        return d;
    }

    @Test
    void 数量达标且无重复且难度曲线由易到难() {
        List<QuestionSummaryDTO> pool = new java.util.ArrayList<>();
        for (long id = 1; id <= 60; id++) pool.add(q(id, "单选题", (int) (id % 5) + 1));
        PaperGenerateEngine engine = new PaperGenerateEngine((s, t, d, l) ->
                pool.stream().filter(q -> q.getDifficulty().equals(d)).limit(l).toList());

        PaperGenerateEngine.Outcome out = engine.generate(1L,
                List.of(Map.of("type", "单选题", "count", 19, "score", 5)), 3, null);

        assertEquals(19, out.questions().size());
        assertEquals(19, out.questions().stream().map(PaperGenerateEngine.Picked::id).collect(Collectors.toSet()).size());
        Set<Integer> diffs = out.questions().stream().map(PaperGenerateEngine.Picked::difficulty).collect(Collectors.toSet());
        assertTrue(out.questions().get(0).difficulty() <= out.questions().get(out.questions().size() - 1).difficulty(),
                "应由易到难: " + diffs);
        assertEquals(95, out.totalScore());
        assertTrue(out.warnings().isEmpty());
    }

    @Test
    void 题量不足时给出告警() {
        PaperGenerateEngine engine = new PaperGenerateEngine((s, t, d, l) -> List.of(q(1, "解答题", 5)));
        PaperGenerateEngine.Outcome out = engine.generate(1L,
                List.of(Map.of("type", "解答题", "count", 10, "score", 12)), 4, null);
        assertEquals(1, out.questions().size());
        assertFalse(out.warnings().isEmpty());
    }

    @Test
    void 排除名单不重复出题() {
        List<QuestionSummaryDTO> pool = List.of(q(1, "单选题", 3), q(2, "单选题", 3));
        PaperGenerateEngine engine = new PaperGenerateEngine((s, t, d, l) -> pool);
        PaperGenerateEngine.Outcome out = engine.generate(1L,
                List.of(Map.of("type", "单选题", "count", 2, "score", 5)), 3, List.of(1L));
        assertEquals(1, out.questions().size());
        assertEquals(2L, out.questions().get(0).id());
    }
}
