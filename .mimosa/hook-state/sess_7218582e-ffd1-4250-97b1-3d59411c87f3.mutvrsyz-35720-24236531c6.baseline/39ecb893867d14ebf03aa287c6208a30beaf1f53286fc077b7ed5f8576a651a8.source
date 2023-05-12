package com.examforge.trade.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 支付回调流水（docs/14 R7 第②板：provider+callback_id 唯一索引防重放，docs/19 §4 对账数据源） */
@Data
@TableName("payment_callback")
public class PaymentCallback {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private String provider;
    private String callbackId;
    private Integer paid;           // 1=回调判定已支付 0=未支付/忽略
    private String payload;
    private LocalDateTime createdAt;
}
