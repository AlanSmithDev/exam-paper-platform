package com.examforge.question.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 纠错工单（docs/10：分类 + 采纳奖励 + 处理闭环） */
@Data
@TableName("feedback")
public class Feedback {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long questionId;
    private Long userId;
    private String targetType;     // 题干错误/属性错误/解析知识性错误/解析细节错误/其他错误
    private String description;
    private String images;         // JSON 数组
    private Integer status;        // 0待处理 1已采纳 2已驳回
    private Integer rewardPoints;
    private String handler;
    private String resolveRemark;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}
