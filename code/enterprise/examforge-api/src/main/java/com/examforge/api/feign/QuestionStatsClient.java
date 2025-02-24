package com.examforge.api.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

/** 题库服务内部统计 */
@FeignClient(name = "examforge-question", path = "/internal/questions", contextId = "questionStats")
public interface QuestionStatsClient {
    @GetMapping("/stats")
    Map<String, Object> stats();
}
