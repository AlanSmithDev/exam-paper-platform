package com.examforge.user.controller;

import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.Result;
import com.examforge.user.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 认证接口（网关白名单放行） */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        return authService.register(body.get("mobile"), body.get("password"), body.get("nickname"), body.get("role"), body.get("inviteCode"));
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        return authService.login(body.get("mobile"), body.get("password"));
    }

    @PostMapping("/admin-login")
    public Result<Map<String, Object>> adminLogin(@RequestBody Map<String, String> body) {
        Result<Map<String, Object>> r = authService.login(body.get("mobile"), body.get("password"));
        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) r.getData().get("profile");
        String role = String.valueOf(profile.get("role"));
        if (!"SUPER_ADMIN".equals(role) && !"OP".equals(role) && !"EDITOR".equals(role)) {
            throw new BizException(Result.UNAUTHORIZED, "该账号无管理端权限");
        }
        return r;
    }

    /** 当前用户（网关透传 X-User-Id） */
    @GetMapping("/me")
    public Result<Map<String, Object>> me(@RequestHeader(value = "X-User-Id", required = false) String uid) {
        if (uid == null) throw new BizException(Result.UNAUTHORIZED, "未登录");
        return Result.ok(Map.of("uid", Long.valueOf(uid)));
    }
}
