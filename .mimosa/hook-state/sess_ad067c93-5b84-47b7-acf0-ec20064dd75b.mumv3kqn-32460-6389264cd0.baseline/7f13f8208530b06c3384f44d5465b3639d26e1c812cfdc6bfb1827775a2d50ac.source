package com.examforge.ai.provider;

import org.springframework.stereotype.Component;

/** 沙箱 Provider：离线返回固定结构，开发与单测用（docs/15 AI-1） */
@Component
public class MockAiProvider implements AiProvider {

    @Override
    public String chat(String system, String user) {
        if (user != null && user.contains("变式题")) {
            return """
                {"stem":"（AI 变式·草稿）已知函数 \\\\(g(x)=\\\\dfrac{x^{2}}{2}-b\\\\ln x\\\\)，讨论 \\\\(g(x)\\\\) 的单调性。",
                 "answer":"b>e 时有两个零点（与原题同构）",
                 "analysis":{"brief":"与原题同构，换参数 b。","solve":"f'(x)=x-b/x=(x²-b)/x…","comment":"注意定义域 x>0。"},
                 "aigc":true}""";
        }
        return """
                {"steps":[
                  {"step":1,"text":"先确认定义域（对数真数大于零）。","latex":"x>0"},
                  {"step":2,"text":"求导并按参数讨论符号。","latex":"f'(x)=x-a/x=(x²-a)/x"},
                  {"step":3,"text":"结合极值符号判断零点个数，注意分类讨论不重不漏。","latex":""}
                ],"aigc":true}""";
    }

    @Override
    public String name() { return "MOCK"; }

    /** MOCK 无视觉能力：显式返回未识别结构，前端降级为人工转录（docs/26 §7 P3 钩子） */
    @Override
    public String chatVision(String system, String user, String imageBase64, String mime) {
        return "{\"recognized\":false,\"reason\":\"MOCK Provider 无视觉能力，请人工转录\",\"answers\":[]}";
    }
}
