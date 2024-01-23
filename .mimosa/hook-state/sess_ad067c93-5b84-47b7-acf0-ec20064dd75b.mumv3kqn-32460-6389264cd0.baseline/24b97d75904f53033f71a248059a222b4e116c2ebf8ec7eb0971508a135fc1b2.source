package com.examforge.ai.provider;

/** AI Provider 抽象（docs/15 AI-1）：MOCK 开发测试 / OPENAI_COMPAT 接 DeepSeek/Qwen/vLLM */
public interface AiProvider {

    /** 单轮对话：system 设定角色与输出格式，user 为任务内容 */
    String chat(String system, String user);

    /** 视觉输入（答题卡 OCR 等多模态任务，docs/26 §7）。默认不支持；多模态 Provider（如 OPENAI_COMPAT 视觉模型）实现之 */
    default String chatVision(String system, String user, String imageBase64, String mime) {
        throw new UnsupportedOperationException(name() + " 不支持视觉输入");
    }

    String name();
}
