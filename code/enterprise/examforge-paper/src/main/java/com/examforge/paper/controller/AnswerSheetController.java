package com.examforge.paper.controller;

import com.examforge.common.web.Result;
import com.examforge.paper.service.AnswerSheetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 答题卡对外接口（e 卷通二阶段：教师端一键生成，docs/26 §7） */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AnswerSheetController {

    private final AnswerSheetService answerSheetService;

    /** 我的试卷 → 答题卡 */
    @GetMapping("/papers/{id}/answer-sheet")
    public Result<Map<String, Object>> forPaper(@RequestHeader("X-User-Id") String uid, @PathVariable Long id) {
        return Result.ok(answerSheetService.forPaper(Long.valueOf(uid), id));
    }

    /** 通用生成：{title, refId, questionIds:[]}（题目顺序=卷面题号顺序） */
    @PostMapping("/answer-sheet")
    public Result<Map<String, Object>> build(@RequestHeader("X-User-Id") String uid,
                                             @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> raw = (List<Object>) body.getOrDefault("questionIds", List.of());
        return Result.ok(answerSheetService.build(String.valueOf(body.getOrDefault("title", "答题卡")),
                body.get("refId") == null ? 0L : Long.valueOf(String.valueOf(body.get("refId"))),
                raw.stream().map(x -> Long.valueOf(String.valueOf(x))).toList()));
    }
}
