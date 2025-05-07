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

    /** 客户端 IP：网关 X-Forwarded-For 首段（直连时回退 X-Real-IP/unknown） */
    private String clientIp(jakarta.servlet.http.HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String real = req.getHeader("X-Real-IP");
        return real == null || real.isBlank() ? "unknown" : real.trim();
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(jakarta.servlet.http.HttpServletRequest req,
                                             @RequestBody Map<String, String> body) {
        return authService.login(body.get("mobile"), body.get("password"), clientIp(req));
    }

    @PostMapping("/admin-login")
    public Result<Map<String, Object>> adminLogin(jakarta.servlet.http.HttpServletRequest req,
                                                  @RequestBody Map<String, String> body) {
        return authService.adminLogin(body.get("mobile"), body.get("password"), clientIp(req), body.get("totp"));
    }

    // ---------- 管理端 TOTP 双因子（docs/20 §6 等保二级；需登录态） ----------

    /** 绑定第一步：生成密钥与 otpauth URI（Authenticator 手动录入） */
    @PostMapping("/totp/setup")
    public Result<Map<String, Object>> totpSetup(@RequestHeader("X-User-Id") String uid) {
        return Result.ok(authService.totpSetup(Long.valueOf(uid)));
    }

    /** 绑定第二步：动态码验证通过后启用 */
    @PostMapping("/totp/enable")
    public Result<Map<String, Object>> totpEnable(@RequestHeader("X-User-Id") String uid,
                                                  @RequestBody Map<String, String> body) {
        return Result.ok(authService.totpEnable(Long.valueOf(uid), body.get("code")));
    }

    /** 双因子状态（管理端安全设置页用；secret 不下发） */
    @GetMapping("/totp/state")
    public Result<Map<String, Object>> totpState(@RequestHeader("X-User-Id") String uid) {
        return Result.ok(authService.totpState(Long.valueOf(uid)));
    }

    /** 解绑（重置密钥） */
    @PostMapping("/totp/disable")
    public Result<Map<String, Object>> totpDisable(@RequestHeader("X-User-Id") String uid) {
        return Result.ok(authService.totpDisable(Long.valueOf(uid)));
    }

    /** 当前用户（网关透传 X-User-Id） */
    @GetMapping("/me")
    public Result<Map<String, Object>> me(@RequestHeader(value = "X-User-Id", required = false) String uid) {
        if (uid == null) throw new BizException(Result.UNAUTHORIZED, "未登录");
        return Result.ok(Map.of("uid", Long.valueOf(uid)));
    }
}
