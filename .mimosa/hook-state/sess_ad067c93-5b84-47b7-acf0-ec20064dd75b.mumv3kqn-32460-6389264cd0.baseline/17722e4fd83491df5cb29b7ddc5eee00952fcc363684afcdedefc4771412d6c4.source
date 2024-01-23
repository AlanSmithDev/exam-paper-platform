package com.examforge.trade.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cdk_batch")
public class CdkBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String batchNo;
    private String rewardType;      // MEMBER_DAYS/POINTS/COUPON
    private Integer rewardDays;
    private Integer rewardPoints;
    private Long rewardTemplateId;
    private Integer total;
    private Integer redeemed;
    private LocalDateTime expireTime;
    private Integer status;         // 1启用 0停用
    private String operator;
    private LocalDateTime createdAt;
}
