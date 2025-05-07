package com.examforge.resource.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("resource_item")
public class ResourceItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private Integer stage;          // 1小学 2初中 3高中 4大学 5考研 6中职
    private String subject;
    private String category;        // PPT课件/教案/学案/作业/试卷/题集/素材/示范课
    private String grade;
    private String format;          // pptx/docx/pdf/zip/mp4
    private Integer pages;
    private Integer sizeKb;
    private String level;           // FREE/NORMAL/SPECIAL/BOUTIQUE
    private Integer priceCents;
    private String author;
    private Long creatorUserId;     // 创作者（上传人）用户ID，分成收益归属（T-26f）
    private String school;
    private Integer previewFreePct;
    private String fileKey;
    private Integer browseCount;
    private Integer downloadCount;
    private Integer status;         // 0待审 1上架 2下架 3驳回
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
