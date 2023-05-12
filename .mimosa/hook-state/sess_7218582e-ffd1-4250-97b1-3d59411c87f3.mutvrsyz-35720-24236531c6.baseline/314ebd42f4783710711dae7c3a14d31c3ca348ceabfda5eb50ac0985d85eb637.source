package com.examforge.api.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/** 用户服务内部端点（统计 + 批量基础信息；全路径声明与 InternalUserController 对齐） */
@FeignClient(name = "examforge-user", path = "/internal/users", contextId = "userStats")
public interface UserStatsClient {

    @GetMapping("/stats")
    Map<String, Object> stats();

    /** 批量取用户基础信息（id+nickname，资源月收入榜昵称展示等场景） */
    @GetMapping("/batch")
    List<Map<String, Object>> batch(@RequestParam("ids") String ids);
}
