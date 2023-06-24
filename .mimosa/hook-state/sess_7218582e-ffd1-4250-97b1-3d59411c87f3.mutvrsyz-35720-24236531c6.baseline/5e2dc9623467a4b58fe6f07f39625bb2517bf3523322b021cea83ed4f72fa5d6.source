package com.examforge.paper.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 试卷模板（题目快照，docs/25 TJ-80/TJ-22） */
@Data
@TableName("paper_template")
public class PaperTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private Long sourcePaperId;
    private String questions;       // JSON [{questionId,score}]（卷面顺序）
    private Integer totalScore;
    private LocalDateTime createdAt;
}
