package com.examforge.admin.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 系统设置（key-value；logo_url 留空 = 前台渲染占位） */
@Data
@TableName("`setting`")
public class Setting {
    @TableId
    private String key;
    private String value;
}
