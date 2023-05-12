package com.examforge.api.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/** AI 服务 Feign 契约（扫描件视觉 OCR，e 卷通二阶段 docs/26 §7） */
@FeignClient(name = "examforge-ai", path = "/internal")
public interface OcrClient {

    /** {userId, imageBase64, mime, questionIds:[]} → {recognized, answers:[{questionId,answer}], reason?, raw, degraded} */
    @PostMapping("/ai/ocr")
    Map<String, Object> ocr(@RequestBody Map<String, Object> body);
}
