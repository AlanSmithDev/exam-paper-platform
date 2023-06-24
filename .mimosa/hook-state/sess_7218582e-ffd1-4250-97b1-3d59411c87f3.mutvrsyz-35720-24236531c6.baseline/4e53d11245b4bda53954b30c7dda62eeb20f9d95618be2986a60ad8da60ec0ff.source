package com.examforge.admin.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 操作审计（超管/运营/编辑写操作，只读不可改） */
@Data
@TableName("audit_log")
public class AuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String actor;
    private String action;
    private String detail;
    private String ip;
    private LocalDateTime createdAt;
}
