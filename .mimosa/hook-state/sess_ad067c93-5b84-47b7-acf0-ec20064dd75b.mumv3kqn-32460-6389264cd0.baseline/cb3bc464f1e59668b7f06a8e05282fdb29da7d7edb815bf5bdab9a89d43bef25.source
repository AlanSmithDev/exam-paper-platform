package com.examforge.paper.controller;

import com.examforge.common.web.Result;
import com.examforge.paper.mapper.PaperMapper;
import com.examforge.paper.service.AnswerSheetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 内部接口（Feign：仅内网，网关不路由 /internal/**） */
@RestController
@RequiredArgsConstructor
public class InternalPaperController {

    private final PaperMapper paperMapper;
    private final AnswerSheetService answerSheetService;

    @GetMapping("/internal/papers/stats")
    public Result<Map<String, Object>> stats() {
        return Result.ok(Map.of("papers", paperMapper.selectCount(null)));
    }

    /** 作业答题卡渲染（practice 服务 Feign 调用，docs/26 F-XKW-12 二阶段） */
    @PostMapping("/internal/answer-sheet")
    public Result<Map<String, Object>> answerSheet(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> raw = (List<Object>) body.getOrDefault("questionIds", List.of());
        return Result.ok(answerSheetService.build(String.valueOf(body.getOrDefault("title", "作业答题卡")),
                body.get("refId") == null ? 0L : Long.valueOf(String.valueOf(body.get("refId"))),
                raw.stream().map(x -> Long.valueOf(String.valueOf(x))).toList()));
    }
}
