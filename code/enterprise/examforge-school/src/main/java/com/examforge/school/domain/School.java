package com.examforge.school.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("school")
public class School {
    public static final int OPEN = 1;
    public static final int CLOSED = 0;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long adminUserId;
    private Integer status;
    private Integer seatLimit;
    private LocalDateTime memberUntil;
    private LocalDateTime createdAt;
}
