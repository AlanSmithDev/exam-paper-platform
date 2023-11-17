package com.examforge.paper.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 试题篮（上限 100，与 docs/12 权益一致） */
@Data
@TableName("basket")
public class Basket {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long questionId;
    private LocalDateTime createdAt;
}
