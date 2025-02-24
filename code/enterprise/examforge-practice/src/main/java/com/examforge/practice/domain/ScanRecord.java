package com.examforge.practice.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("scan_record")
public class ScanRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long assignmentId;
    private Long studentId;
    private String fileName;
    private String storedPath;
    private String mime;
    private Integer sizeBytes;
    private String status;          // UPLOADED/RECOGNIZED/IMPORTED
    private String ocrJson;         // P3：AI 视觉输出 [{questionId,answer}]
    private Long uploadedBy;
    private LocalDateTime createdAt;
}
