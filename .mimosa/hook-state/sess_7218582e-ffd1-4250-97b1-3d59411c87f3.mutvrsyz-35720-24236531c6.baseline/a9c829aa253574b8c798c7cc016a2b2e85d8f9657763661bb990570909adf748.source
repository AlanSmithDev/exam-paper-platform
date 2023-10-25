package com.examforge.trade.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_coupon")
public class UserCoupon {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long templateId;
    private Long userId;
    private Integer status;         // 0未用 1已用 2过期
    private LocalDateTime expireTime;
    private String usedOrder;
    private LocalDateTime createdAt;
}
