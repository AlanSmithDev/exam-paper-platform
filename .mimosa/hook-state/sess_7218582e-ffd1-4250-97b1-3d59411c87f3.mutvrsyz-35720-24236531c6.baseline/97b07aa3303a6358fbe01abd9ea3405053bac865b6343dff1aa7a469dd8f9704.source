package com.examforge.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.Result;
import com.examforge.user.domain.User;
import com.examforge.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 邀请裂变（docs/16 I-6） */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class InviteController {

    private final UserMapper userMapper;

    @GetMapping("/me/invite")
    public Result<Map<String, Object>> myInvite(@RequestHeader("X-User-Id") String uid) {
        User u = userMapper.selectById(Long.valueOf(uid));
        if (u == null) throw new BizException(Result.UNAUTHORIZED, "用户不存在");
        long invited = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getInvitedBy, u.getId()));
        return Result.ok(Map.of("inviteCode", u.getInviteCode() == null ? "" : u.getInviteCode(),
                "invitedCount", invited, "rewardPerInvite", 20));
    }
}
