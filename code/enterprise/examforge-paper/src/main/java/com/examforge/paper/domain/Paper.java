package com.examforge.paper.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 试卷（定稿后题目以快照存储，内容不漂移） */
@Data
@TableName("paper")
public class Paper {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String blueprint;      // 组卷蓝图 JSON
    private Integer totalScore;
    private Integer status;        // 0编辑 1定稿
    private LocalDateTime createdAt;
}
