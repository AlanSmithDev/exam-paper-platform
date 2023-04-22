package com.examforge.school.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("school_member")
public class SchoolMember {
    public static final String TEACHER = "TEACHER";
    public static final String STUDENT = "STUDENT";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long schoolId;
    private Long userId;
    private String role;
    private LocalDateTime joinedAt;
}
