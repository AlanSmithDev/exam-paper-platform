package com.examforge.practice.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("assignment_answer")
public class AssignmentAnswer {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long assignmentId;
    private Long studentId;
    private Long questionId;
    private String answer;
    private Integer correct;        // 1对 0错 NULL待批改
    private BigDecimal score;       // 教师批改给分（解答题）
    private Integer durationMs;
    private LocalDateTime createdAt;
}
