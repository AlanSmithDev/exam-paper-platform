package com.examforge.ai.provider;

import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * OpenAI 兼容 Provider（DeepSeek / 通义 Qwen / 自建 vLLM 均兼容）：
 * POST {base-url}/chat/completions，Bearer 鉴权，解析 choices[0].message.content。
 * API Key 从环境变量注入，禁止硬编码。
 */
@Component
@Lazy
public class OpenAiCompatProvider implements AiProvider {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String baseUrl, apiKey, model;
    private final int timeoutSeconds;

    public OpenAiCompatProvider(@Value("${examforge.ai.base-url}") String baseUrl,
                                @Value("${examforge.ai.api-key}") String apiKey,
                                @Value("${examforge.ai.model}") String model,
                                @Value("${examforge.ai.timeout-seconds:15}") int timeoutSeconds) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public String chat(String system, String user) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BizException(Result.BAD_REQUEST, "未配置 AI_API_KEY");
        }
        String body = """
                {"model":"%s","messages":[
                  {"role":"system","content":"%s"},
                  {"role":"user","content":"%s"}],
                 "temperature":0.7}
                """.formatted(model, esc(system), esc(user));
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                throw new BizException(Result.SYSTEM, "AI 上游错误 " + resp.statusCode());
            }
            return extractContent(resp.body());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(Result.SYSTEM, "AI 调用失败：" + e.getMessage());
        }
    }

    /** 视觉输入：多模态消息（text + image_url base64 data URL），供答题卡 OCR 等任务（docs/26 §7） */
    @Override
    public String chatVision(String system, String user, String imageBase64, String mime) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BizException(Result.BAD_REQUEST, "未配置 AI_API_KEY");
        }
        String body = """
                {"model":"%s","messages":[
                  {"role":"system","content":"%s"},
                  {"role":"user","content":[
                    {"type":"text","text":"%s"},
                    {"type":"image_url","image_url":{"url":"data:%s;base64,%s"}}]}],
                 "temperature":0.2}
                """.formatted(model, esc(system), esc(user), mime, imageBase64);
        try {
            // 视觉推理较文本慢，超时下限 30s
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(Math.max(timeoutSeconds, 30)))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 300) {
                throw new BizException(Result.SYSTEM, "AI 上游错误 " + resp.statusCode());
            }
            return extractContent(resp.body());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(Result.SYSTEM, "AI 视觉调用失败：" + e.getMessage());
        }
    }

    /** 轻量解析 choices[0].message.content（避免引 JSON 库） */
    private String extractContent(String resp) {
        int i = resp.indexOf("\"content\":\"");
        if (i < 0) return resp;
        int s = i + 11, e = resp.indexOf("\"", s);
        while (e > 0 && resp.charAt(e - 1) == '\\') e = resp.indexOf("\"", e + 1);
        return e < 0 ? resp.substring(s) : resp.substring(s, e).replace("\\n", "\n").replace("\\\"", "\"");
    }

    private String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    @Override
    public String name() { return "OPENAI_COMPAT:" + model; }
}
