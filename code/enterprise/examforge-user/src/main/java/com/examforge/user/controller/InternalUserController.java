package com.examforge.user.controller;

import com.examforge.common.web.Result;
import com.examforge.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 内部端点（Feign：仅内网，网关不路由 /internal/**） */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserMapper userMapper;

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.ok(Map.of("users", userMapper.selectCount(null)));
    }

    /** 批量取用户基础信息（资源月收入榜昵称展示等场景；仅暴露 id+nickname 公开字段，一批 ≤100） */
    @GetMapping("/batch")
    public Result<List<Map<String, Object>>> batch(@RequestParam("ids") String ids) {
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> {
                    try { return Long.valueOf(s); } catch (NumberFormatException e) { return null; }
                })
                .filter(Objects::nonNull).distinct().limit(100).toList();
        if (idList.isEmpty()) return Result.ok(List.of());
        return Result.ok(userMapper.selectBatchIds(idList).stream()
                .map(u -> Map.<String, Object>of("id", u.getId(),
                        "nickname", u.getNickname() == null ? "" : u.getNickname()))
                .toList());
    }
}
