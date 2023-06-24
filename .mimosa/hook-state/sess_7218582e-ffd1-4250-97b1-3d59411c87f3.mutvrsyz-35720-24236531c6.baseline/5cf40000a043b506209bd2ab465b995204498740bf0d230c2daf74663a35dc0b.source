package com.examforge.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.examforge.api.feign.TradeClient;
import com.examforge.common.security.JwtUtil;
import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.Result;
import com.examforge.user.domain.LoginAttempt;
import com.examforge.user.domain.User;
import com.examforge.user.logic.LoginGuardRules;
import com.examforge.user.logic.TotpRules;
import com.examforge.user.mapper.LoginAttemptMapper;
import com.examforge.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.SplittableRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final TradeClient tradeClient;
    private final LoginAttemptMapper attemptMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SplittableRandom random = new SplittableRandom();

    public Result<Map<String, Object>> register(String mobile, String password, String nickname, String role, String inviteCode) {
        if (mobile == null || !mobile.matches("^1\\d{10}$") || password == null || password.length() < 8) {
            throw new BizException(Result.BAD_REQUEST, "手机号或密码格式错误（密码≥8位）");
        }
        String r = "STUDENT".equals(role) ? "STUDENT" : "TEACHER";
        if (userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getMobile, mobile)) != null) {
            throw new BizException(Result.BAD_REQUEST, "手机号已注册");
        }
        User u = new User();
        u.setMobile(mobile);
        u.setPasswordHash(encoder.encode(password));
        u.setNickname(nickname == null || nickname.isBlank() ? mobile.substring(mobile.length() - 4) : nickname);
        u.setRole(r);
        u.setStatus(1);
        u.setCertify(0);
        u.setInviteCode(genInviteCode());
        // I-2 受邀注册：记录邀请人（无效/自邀静默忽略，不阻断注册）
        if (inviteCode != null && !inviteCode.isBlank()) {
            User inviter = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getInviteCode, inviteCode.trim()));
            if (inviter != null) u.setInvitedBy(inviter.getId());
        }
        userMapper.insert(u);
        grantRewards(u);   // I-3/I-4 新人礼包与邀请奖励（fail-open）
        return Result.ok(tokenOf(u));
    }

    /** I-3 新人 +10 点；I-4 邀请人 +20 点（自邀不发；trade 不可用不阻断） */
    private void grantRewards(User u) {
        try {
            tradeClient.reward(String.valueOf(u.getId()), Map.of("points", 10, "reason", "NEWBIE", "ref", "register"));
            if (u.getInvitedBy() != null && !u.getInvitedBy().equals(u.getId())) {
                tradeClient.reward(String.valueOf(u.getInvitedBy()), Map.of("points", 20, "reason", "INVITE", "ref", "U" + u.getId()));
            }
        } catch (Exception e) {
            log.warn("注册奖励发放失败（fail-open）: {}", e.getMessage());
        }
    }

    private String genInviteCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 6; i++) b.append(alphabet.charAt(random.nextInt(alphabet.length())));
            if (userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getInviteCode, b.toString())) == null) {
                return b.toString();
            }
        }
        return null;   // 极小概率冲突，邀请码允许为空
    }

    /**
     * 登录（docs/20 §6 防爆破）：窗口内同源失败≥5 → 临时锁定 15min（锁定期内不验密码直接拒绝）；
     * 每次尝试落 login_attempt 流水（等保审计留痕）。ip 取网关透传 X-Forwarded-For，缺省 "unknown"。
     */
    public Result<Map<String, Object>> login(String mobile, String password, String ip) {
        User u = guardAndAuth(mobile, password, ip);
        return Result.ok(tokenOf(u));
    }

    /**
     * 管理端登录（docs/20 §6 等保二级双因子）：角色校验 + **TOTP 已启用则强制校验动态码**——
     * 缺失/错误抛 NEED_TOTP（不签发 token），前端引导重登并携带动态码。
     */
    public Result<Map<String, Object>> adminLogin(String mobile, String password, String ip, String totpCode) {
        User u = guardAndAuth(mobile, password, ip);
        if (!"SUPER_ADMIN".equals(u.getRole()) && !"OP".equals(u.getRole()) && !"EDITOR".equals(u.getRole())) {
            throw new BizException(Result.UNAUTHORIZED, "该账号无管理端权限");
        }
        verifyTotpIfEnabled(u, totpCode);
        return Result.ok(tokenOf(u));
    }

    /** 防爆破守卫 + 密码认证（login/adminLogin 共用） */
    private User guardAndAuth(String mobile, String password, String ip) {
        LocalDateTime now = LocalDateTime.now();
        // 防爆破锁定判定（按 mobile+ip 同源；fail-open：流水查询异常不阻断登录）
        try {
            var recent = attemptMapper.selectList(new LambdaQueryWrapper<LoginAttempt>()
                    .eq(LoginAttempt::getMobile, mobile)
                    .eq(LoginAttempt::getIp, ip == null || ip.isBlank() ? "unknown" : ip)
                    .eq(LoginAttempt::getSuccess, 0)
                    .gt(LoginAttempt::getCreatedAt, LoginGuardRules.windowStart(now))
                    .orderByDesc(LoginAttempt::getCreatedAt)
                    .last("LIMIT " + LoginGuardRules.FAIL_LIMIT));
            LocalDateTime until = LoginGuardRules.lockedUntil(recent.size(),
                    recent.isEmpty() ? null : recent.get(0).getCreatedAt(), now);
            if (until != null) {
                long remain = LoginGuardRules.lockRemainSeconds(until, now);
                log.warn("登录锁定触发: mobile={} ip={} 剩余{}s", mobile, ip, remain);
                throw new BizException(Result.TOO_MANY, "登录尝试过于频繁，账户已临时锁定，请 " + Math.max(remain / 60 + 1, 1) + " 分钟后再试");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("登录防爆破判定不可用（fail-open）: {}", e.getMessage());
        }

        User u = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getMobile, mobile));
        boolean ok = u != null && encoder.matches(password == null ? "" : password, u.getPasswordHash());
        record(mobile, ip, ok);   // 审计留痕（每次尝试）
        if (!ok) throw new BizException(Result.UNAUTHORIZED, "手机号或密码错误");
        if (u.getStatus() == 0) throw new BizException(Result.FORBIDDEN, "账号已封禁");
        return u;
    }

    /** TOTP 已启用则强制校验（缺失/错误 → NEED_TOTP，不发放凭证） */
    private void verifyTotpIfEnabled(User u, String totpCode) {
        if (u.getTotpEnabled() != null && u.getTotpEnabled() == 1) {
            boolean pass = u.getTotpSecret() != null
                    && TotpRules.verify(u.getTotpSecret(), totpCode, System.currentTimeMillis() / 1000);
            if (!pass) throw new BizException(Result.UNAUTHORIZED, "NEED_TOTP: 管理端双因子动态码缺失或错误");
        }
    }

    /** TOTP 绑定第一步：生成密钥（未启用状态写入，enable 验证通过后才强制） */
    public Map<String, Object> totpSetup(Long uid) {
        User u = userMapper.selectById(uid);
        if (u == null) throw new BizException(Result.NOT_FOUND, "用户不存在");
        String secret = TotpRules.generateSecret();
        u.setTotpSecret(secret);
        u.setTotpEnabled(0);
        userMapper.updateById(u);
        return Map.of("secret", secret,
                "otpauthUri", TotpRules.otpauthUri(secret, u.getMobile(), "SmartPaperCloud-Admin"),
                "hint", "请用 Authenticator 手动录入密钥，输入 6 位动态码完成启用");
    }

    /** TOTP 绑定第二步：动态码验证通过后启用（此后 admin-login 强制校验） */
    public Map<String, Object> totpEnable(Long uid, String code) {
        User u = userMapper.selectById(uid);
        if (u == null) throw new BizException(Result.NOT_FOUND, "用户不存在");
        if (u.getTotpSecret() == null) throw new BizException(Result.BAD_REQUEST, "请先 setup 生成密钥");
        if (!TotpRules.verify(u.getTotpSecret(), code, System.currentTimeMillis() / 1000)) {
            throw new BizException(Result.BAD_REQUEST, "动态码校验失败，请确认时间同步后重试");
        }
        u.setTotpEnabled(1);
        userMapper.updateById(u);
        log.info("管理端 TOTP 已启用: userId={}", uid);
        return Map.of("enabled", true);
    }

    /** TOTP 状态（管理端安全设置页；secret 不下发） */
    public Map<String, Object> totpState(Long uid) {
        User u = userMapper.selectById(uid);
        if (u == null) throw new BizException(Result.NOT_FOUND, "用户不存在");
        return Map.of("enabled", u.getTotpEnabled() != null && u.getTotpEnabled() == 1,
                "configured", u.getTotpSecret() != null);
    }

    /** TOTP 解绑（重置密钥与启用状态） */
    public Map<String, Object> totpDisable(Long uid) {
        User u = userMapper.selectById(uid);
        if (u == null) throw new BizException(Result.NOT_FOUND, "用户不存在");
        u.setTotpEnabled(0);
        u.setTotpSecret(null);
        userMapper.updateById(u);
        return Map.of("enabled", false);
    }

    /** 尝试流水落库（fail-open：审计失败不阻断登录主流程） */
    private void record(String mobile, String ip, boolean success) {
        try {
            LoginAttempt a = new LoginAttempt();
            a.setMobile(mobile);
            a.setIp(ip == null || ip.isBlank() ? "unknown" : ip);
            a.setSuccess(success ? 1 : 0);
            a.setCreatedAt(LocalDateTime.now());
            attemptMapper.insert(a);
        } catch (Exception e) {
            log.warn("登录流水落库失败（fail-open）: {}", e.getMessage());
        }
    }

    private Map<String, Object> tokenOf(User u) {
        return Map.of(
                "accessToken", jwtUtil.sign(u.getId(), u.getRole(), u.getNickname()),
                "profile", Map.of("id", u.getId(), "mobile", u.getMobile(), "nickname", u.getNickname(), "role", u.getRole())
        );
    }
}
