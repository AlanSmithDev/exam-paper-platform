package com.examforge.resource.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 创作者分成账本（下载计费成功即记账，docs/26 §6；P3 月度结算补发即时入账失败行） */
@Data
@TableName("creator_earning")
public class CreatorEarning {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long creatorUserId;
    private Long resourceId;
    private Long downloaderId;
    private Integer amountCents;    // 下载实收点数
    private Integer shareCents;     // 创作者分成点数
    private Integer ratePct;        // 分成比例%（记账时点快照）
    private Integer creditStatus;   // 入账状态：1已入账 0待结算补发（docs/22 2.3）
    private Long settlementId;      // 结算单ID（补发入账后回填）
    private LocalDateTime createdAt;
}
