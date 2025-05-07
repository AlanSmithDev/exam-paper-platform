package com.examforge.resource.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("copyright_appeal")
public class CopyrightAppeal {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String targetType;      // RESOURCE/QUESTION/PAPER
    private Long targetId;
    private String appealType;      // COPYRIGHT/ERROR/OTHER
    private String content;
    private String contact;
    private String status;          // OPEN/RESOLVED/REJECTED/WITHDRAWN
    private String handleRemark;
    private String handler;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}
