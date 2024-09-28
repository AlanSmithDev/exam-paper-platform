package com.examforge.ai.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_log")
public class AiLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String scene;        // VARIANT/EXPLAIN
    private String provider;
    private String model;
    private Integer promptChars;
    private Integer respChars;
    private Integer costMs;
    private Integer degraded;
    private LocalDateTime createdAt;
}
