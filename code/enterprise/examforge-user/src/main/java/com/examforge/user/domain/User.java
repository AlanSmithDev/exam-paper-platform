package com.examforge.user.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户（RBAC：SUPER_ADMIN/OP/EDITOR/TEACHER/STUDENT） */
@Data
@TableName("`user`")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String mobile;
    private String passwordHash;
    private String nickname;
    private String role;
    private Integer status;        // 1 正常 0 封禁
    private Integer certify;       // 教师认证 0/1
    private String memberUntil;
    private String inviteCode;     // 我的邀请码（6位 base36，docs/16 I-1）
    private Long invitedBy;        // 邀请人
    private String totpSecret;     // TOTP 密钥（Base32，docs/20 等保二级双因子；null=未 setup）
    private Integer totpEnabled;   // 1=已启用（admin-login 强制校验动态码）
    private LocalDateTime createdAt;
}
