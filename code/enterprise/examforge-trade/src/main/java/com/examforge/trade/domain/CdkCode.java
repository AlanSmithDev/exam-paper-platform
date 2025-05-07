package com.examforge.trade.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cdk_code")
public class CdkCode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private Long batchId;
    private Integer status;         // 0未用 1已用
    private Long usedBy;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;
}
