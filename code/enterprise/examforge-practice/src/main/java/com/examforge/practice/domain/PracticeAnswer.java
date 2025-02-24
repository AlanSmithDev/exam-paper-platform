package com.examforge.practice.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("practice_answer")
public class PracticeAnswer {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long practiceId;
    private Long questionId;
    private String answer;
    private Integer correct;        // 1对 0错 NULL待批改
    private Integer durationMs;
    private LocalDateTime createdAt;
}
