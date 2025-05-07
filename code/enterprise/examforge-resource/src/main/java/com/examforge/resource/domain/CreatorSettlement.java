package com.examforge.resource.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 创作者分成月度结算单（P3 补发，docs/26 §6：每成功补发一批生成一单，同月可能多单=分批补发） */
@Data
@TableName("creator_settlement")
public class CreatorSettlement {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long creatorUserId;
    private String month;           // 结算月 yyyy-MM（按账本 created_at 自然月）
    private Integer shareCents;     // 本单补发分成点数合计
    private Integer rowCount;       // 本单覆盖账本行数
    private Integer status;         // 1=已入账
    private LocalDateTime creditedAt;
    private LocalDateTime createdAt;
}
