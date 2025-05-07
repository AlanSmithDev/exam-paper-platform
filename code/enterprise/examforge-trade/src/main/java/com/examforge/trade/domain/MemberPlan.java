package com.examforge.trade.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("member_plan")
public class MemberPlan {
    @TableId
    private String planId;
    private String name;
    private Integer priceCents;
    private Integer durationDays;
    private String benefits;
    private Integer status;
}
