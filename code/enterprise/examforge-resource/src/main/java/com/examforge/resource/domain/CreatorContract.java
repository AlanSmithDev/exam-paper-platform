package com.examforge.resource.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 创作者签约合同（docs/26 §6：主体/比例/结算周期；签约比例优先于全局默认） */
@Data
@TableName("creator_contract")
public class CreatorContract {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long creatorUserId;
    private String subject;      // 签约主体（实名/笔名/机构）
    private Integer ratePct;     // 签约分成比例%（覆盖全局默认）
    private String settleCycle;  // 结算周期（当前仅 MONTHLY）
    private String status;       // ACTIVE=生效中 ENDED=已结束
    private LocalDateTime startAt;
    private LocalDateTime endAt; // NULL=长期有效
    private LocalDateTime createdAt;
}
