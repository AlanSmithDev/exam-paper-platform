package com.examforge.ai.provider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Mock Provider 与降级路径单测（docs/15 §4） */
class MockAiProviderTest {

    private final MockAiProvider provider = new MockAiProvider();

    @Test
    void 出题场景_返回含变式题标记的JSON() {
        String out = provider.chat("system", "请生成一道变式题");
        assertTrue(out.contains("变式"));
        assertTrue(out.contains("aigc"));
        assertTrue(out.contains("stem"));
    }

    @Test
    void 讲题场景_返回分步JSON() {
        String out = provider.chat("system", "请讲题：f(x)=x²/2-a ln x");
        assertTrue(out.contains("steps"));
        assertTrue(out.contains("latex"));
    }

    @Test
    void 教学设计场景_返回结构化教案JSON() throws Exception {
        String out = provider.chat("system", "请生成教学设计。知识点：函数的单调性。");
        var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(out);
        assertTrue(root.has("objectives") && root.get("objectives").isArray());
        assertTrue(root.has("keyPoints") && root.has("difficulties"));
        assertTrue(root.has("process") && root.get("process").isArray(), "教学过程四环节");
        assertTrue(root.has("board"), "板书设计");
        assertTrue(root.path("aigc").asBoolean(false), "aigc 草稿标识");
    }

    @Test
    void 名称标识() {
        assertEquals("MOCK", provider.name());
    }

    @Test
    void 视觉输入_MOCK显式返回未识别结构() throws Exception {
        String resp = provider.chatVision("sys", "user", "aGk=", "image/jpeg");
        var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(resp);
        assertFalse(root.path("recognized").asBoolean(true), "MOCK 必须显式返回未识别，驱动人工转录降级");
        assertTrue(root.path("answers").isArray());
        assertFalse(root.path("reason").asText().isBlank());
    }

    @Test
    void 视觉输入_拍照搜题场景返回确定性题干() throws Exception {
        // 提示词含"题干"标记即拍照搜题场景（docs/23 §3A）：返回确定性伪题干驱动全链路离线演示
        String resp = provider.chatVision("你是题目图片识别器。从照片中提取题目题干文本。", "user", "aGk=", "image/png");
        var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(resp);
        assertTrue(root.path("recognized").asBoolean(false));
        assertFalse(root.path("stem").asText().isBlank());
        assertTrue(root.path("stem").asText().contains("函数"), "伪题干须含可检索词，MOCK 下检索链路可命中");
        // 扫描转录场景合同不受影响：不含"题干"标记仍走未识别
        String scan = provider.chatVision("你是答题卡扫描件识别器。", "user", "aGk=", "image/jpeg");
        assertFalse(new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(scan).path("recognized").asBoolean(true));
    }
}
