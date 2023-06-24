package com.examforge.practice.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("assignment_student")
public class AssignmentStudent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long assignmentId;
    private Long studentId;
    private Integer status;         // 0待完成 1已提交 2已批改
    private BigDecimal score;       // 正确率 %
    private Integer late;           // 超时提交
    private LocalDateTime submitAt;
    private LocalDateTime gradedAt;
    private LocalDateTime createdAt;
}
