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
}
