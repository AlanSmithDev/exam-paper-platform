package com.examforge.admin.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 广告位（position 枚举见 docs/12：home_hero/home_banner/sidebar_teacher/sidebar_student/list_inline/detail_footer/login_promo） */
@Data
@TableName("ad")
public class Ad {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String position;
    private String title;
    private String imageUrl;     // 空则前台渲染占位框
    private String linkUrl;
    private String audience;     // ALL/TEACHER/STUDENT
    private Integer sort;
    private Integer status;      // 1在投 0下线
    private LocalDateTime createdAt;
}
