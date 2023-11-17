package com.examforge.trade.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("task_def")
public class TaskDef {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskKey;
    private String name;
    private Integer rewardPoints;
    private Integer daily;          // 1每日 0一次性
    private Integer status;
    private LocalDateTime createdAt;
}
