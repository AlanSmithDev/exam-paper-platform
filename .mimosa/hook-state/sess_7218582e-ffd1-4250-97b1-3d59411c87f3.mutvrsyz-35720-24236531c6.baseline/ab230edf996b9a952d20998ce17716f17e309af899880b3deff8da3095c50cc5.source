package com.examforge.question.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 题目主表（内容 JSON Block 化：text/latex/figure，见 docs/04 与 docs/06） */
@Data
@TableName("question")
public class Question {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long subjectId;
    private String type;            // 单选题/多选题/填空题/解答题/判断题
    private Integer difficulty;     // 1易 2较易 3中 4较难 5难
    private Double coefficient;     // 难度系数 0~1
    private String scene;           // 预习/课堂/作业/单元测试/阶段检测/高考...
    private String category;        // 典型题/压轴题/同步题/易错题/常考题/好题/新定义
    private String kpNames;         // 知识点（逗号分隔）
    private String literacy;        // 核心素养（逗号分隔）
    private String source;          // 来源卷描述
    private String stem;            // 题干（含 LaTeX \( \) 行内式）
    private String options;         // JSON [{l,v}]
    private String answer;
    private String analysis;        // JSON {brief,solve,comment} 五段式精简存储
    private String author;
    private String reviewer;
    private Integer status;         // 0草稿 1审核中 2上架 3下架
    private Integer useCount;
    private Integer aigc;
    private LocalDateTime createdAt;
}
