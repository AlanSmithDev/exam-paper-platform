package com.examforge.trade.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("task_record")
public class TaskRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String taskKey;
    private String period;          // yyyymmdd 或 LIFETIME
    private LocalDateTime createdAt;
}
