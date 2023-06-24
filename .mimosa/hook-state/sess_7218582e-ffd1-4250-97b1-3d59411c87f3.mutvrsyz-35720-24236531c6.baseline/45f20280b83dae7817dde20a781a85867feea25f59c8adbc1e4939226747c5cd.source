package com.examforge.trade.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("trade_order")
public class TradeOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private String skuType;         // MEMBER/POINTS/PAPER
    private String skuRef;
    private Integer quantity;
    private Integer amountCents;
    private Integer discountCents;
    private Integer payCents;
    private Long couponId;
    private String status;          // CREATED/PAID/CLOSED/REFUNDED
    private String idempotencyKey;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
}
