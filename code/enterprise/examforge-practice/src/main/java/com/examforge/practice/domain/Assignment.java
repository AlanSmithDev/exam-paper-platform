package com.examforge.practice.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("assignment")
public class Assignment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teacherId;
    private String title;
    private Long subjectId;
    private String questionIds;     // JSON 数组文本
    private LocalDateTime deadline;
    private Integer status;         // 0草稿 1已发布 2已关闭
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
