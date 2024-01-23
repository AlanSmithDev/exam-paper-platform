package com.examforge.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.examforge.api.feign.TradeClient;
import com.examforge.common.security.JwtUtil;
import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.Result;
import com.examforge.user.domain.User;
import com.examforge.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.SplittableRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final TradeClient tradeClient;
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

    public Result<Map<String, Object>> login(String mobile, String password) {
        User u = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getMobile, mobile));
        if (u == null || !encoder.matches(password == null ? "" : password, u.getPasswordHash())) {
            throw new BizException(Result.UNAUTHORIZED, "手机号或密码错误");
        }
        if (u.getStatus() == 0) throw new BizException(Result.FORBIDDEN, "账号已封禁");
        return Result.ok(tokenOf(u));
    }

    private Map<String, Object> tokenOf(User u) {
        return Map.of(
                "accessToken", jwtUtil.sign(u.getId(), u.getRole(), u.getNickname()),
                "profile", Map.of("id", u.getId(), "mobile", u.getMobile(), "nickname", u.getNickname(), "role", u.getRole())
        );
    }
}
