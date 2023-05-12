package com.examforge.user.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 登录尝试流水（docs/20 §6 防爆破判定依据 + 等保审计留痕） */
@Data
@TableName("login_attempt")
public class LoginAttempt {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String mobile;
    private String ip;
    private Integer success;        // 1=成功 0=失败
    private LocalDateTime createdAt;
}
