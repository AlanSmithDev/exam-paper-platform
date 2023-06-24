package com.examforge.school.controller;

import com.examforge.common.web.Result;
import com.examforge.school.service.SchoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 学校订阅校管理员端（网关 /api/v1/schools/**，登录态；管理员身份以 school.admin_user_id 为准） */
@RestController
@RequestMapping("/api/v1/schools")
@RequiredArgsConstructor
public class SchoolController {

    private final SchoolService schoolService;

    /** 我的学校概览（订阅状态/席位/教师数） */
    @GetMapping("/mine")
    public Result<Map<String, Object>> mine(@RequestHeader("X-User-Id") String uid) {
        return Result.ok(schoolService.mine(Long.valueOf(uid)));
    }

    /** 批量添加成员：{members:[{userId, role?}]}（席位校验/用户存在性/去重幂等） */
    @PostMapping("/mine/members")
    public Result<Map<String, Object>> addMembers(@RequestHeader("X-User-Id") String uid,
                                                  @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> raw = (List<Object>) body.getOrDefault("members", List.of());
        return Result.ok(schoolService.addMembers(Long.valueOf(uid),
                raw.stream().map(x -> (Map<String, Object>) x).toList()));
    }

    /** 成员名单 */
    @GetMapping("/mine/members")
    public Result<List<Map<String, Object>>> members(@RequestHeader("X-User-Id") String uid) {
        return Result.ok(schoolService.listMembers(Long.valueOf(uid)));
    }

    /** 移除成员（管理员本人不可移除） */
    @DeleteMapping("/mine/members/{userId}")
    public Result<Map<String, Object>> removeMember(@RequestHeader("X-User-Id") String uid,
                                                    @PathVariable Long userId) {
        return Result.ok(schoolService.removeMember(Long.valueOf(uid), userId));
    }
}
