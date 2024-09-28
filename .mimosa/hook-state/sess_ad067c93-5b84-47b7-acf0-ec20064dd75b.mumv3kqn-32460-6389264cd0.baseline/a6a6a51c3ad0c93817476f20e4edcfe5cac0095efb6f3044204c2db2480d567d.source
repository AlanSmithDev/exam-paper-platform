package com.examforge.api.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/** 组卷服务 Feign 契约（答题卡渲染，e 卷通二阶段 docs/26 §7） */
@FeignClient(name = "examforge-paper", path = "/internal")
public interface AnswerSheetClient {

    /** 作业答题卡：{title, refId, questionIds:[]} → {downloadUrl, format, questionCount} */
    @PostMapping("/answer-sheet")
    Map<String, Object> answerSheet(@RequestBody Map<String, Object> body);
}
