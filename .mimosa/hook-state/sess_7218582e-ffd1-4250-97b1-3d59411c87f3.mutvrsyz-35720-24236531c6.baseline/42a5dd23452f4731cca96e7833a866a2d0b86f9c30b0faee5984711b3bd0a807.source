package com.examforge.trade.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("member")
public class Member {
    @TableId
    private Long userId;
    private String planId;
    private LocalDateTime expireTime;
    private LocalDateTime updatedAt;
}
