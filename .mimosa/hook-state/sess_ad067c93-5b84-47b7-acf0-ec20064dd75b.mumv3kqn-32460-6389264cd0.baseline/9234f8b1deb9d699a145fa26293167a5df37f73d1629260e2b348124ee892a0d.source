package com.examforge.ai.controller;

import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.Result;
import com.examforge.ai.service.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** AI 对外接口（网关 /api/v1/ai/**；配额对接 trade 为 M2） */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @PostMapping("/generate-question")
    public Result<Map<String, Object>> generateVariant(@RequestHeader("X-User-Id") String uid,
                                                       @RequestBody Map<String, String> body) {
        String stem = body.get("stem");
        if (stem == null || stem.isBlank()) throw new BizException(Result.BAD_REQUEST, "stem 必填");
        return Result.ok(aiService.generateVariant(Long.valueOf(uid), stem, body.get("meta")));
    }

    @PostMapping("/explain")
    public Result<Map<String, Object>> explain(@RequestHeader("X-User-Id") String uid,
                                               @RequestBody Map<String, String> body) {
        String stem = body.get("stem");
        if (stem == null || stem.isBlank()) throw new BizException(Result.BAD_REQUEST, "stem 必填");
        return Result.ok(aiService.explain(Long.valueOf(uid), stem, body.get("answer")));
    }

    /** S-1~S-3 AI 搜：自然语言 → 意图解析 → 题库检索 */
    @PostMapping("/search")
    public Result<Map<String, Object>> search(@RequestHeader("X-User-Id") String uid,
                                              @RequestBody Map<String, String> body) {
        String query = body.get("query");
        if (query == null || query.isBlank()) throw new BizException(Result.BAD_REQUEST, "query 必填");
        return Result.ok(aiService.aiSearch(Long.valueOf(uid), query));
    }

    /** SSE 流式讲题：逐步推送讲解步骤（text/event-stream） */
    @GetMapping(value = "/explain/stream", produces = "text/event-stream")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter explainStream(
            @RequestHeader("X-User-Id") String uid,
            @RequestParam String stem,
            @RequestParam(required = false) String answer) {
        var emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(30_000L);
        new Thread(() -> {
            try {
                for (var step : aiService.explainSteps(stem, answer)) {
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                            .name("step").data(step));
                    Thread.sleep(400);
                }
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("done").data("true"));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }).start();
        return emitter;
    }
}
