package com.examforge.ai.controller;

import com.examforge.ai.service.AiService;
import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** AI 内部接口（Feign：仅内网，网关不路由 /internal/**） */
@RestController
@RequestMapping("/internal/ai")
@RequiredArgsConstructor
public class InternalAiController {

    private final AiService aiService;

    /** 扫描件视觉 OCR（practice 服务 Feign 调用，e 卷通二阶段 P3 钩子，docs/26 §7） */
    @PostMapping("/ocr")
    public Result<Map<String, Object>> ocr(@RequestBody Map<String, Object> body) {
        Long teacherId = Long.valueOf(String.valueOf(body.get("userId")));
        String imageBase64 = String.valueOf(body.getOrDefault("imageBase64", ""));
        if (imageBase64.isBlank()) throw new BizException(Result.BAD_REQUEST, "imageBase64 必填");
        String mime = String.valueOf(body.getOrDefault("mime", "image/jpeg"));
        @SuppressWarnings("unchecked")
        List<Object> raw = (List<Object>) body.getOrDefault("questionIds", List.of());
        List<Long> questionIds = raw.stream().map(x -> Long.valueOf(String.valueOf(x))).toList();
        return Result.ok(aiService.ocrScan(teacherId, imageBase64, mime, questionIds));
    }
}
