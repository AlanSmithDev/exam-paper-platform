package com.examforge.api.dto;

import lombok.Data;

/** 题目传输对象（服务间：组卷取候选题 / 导出取全量内容） */
@Data
public class QuestionSummaryDTO {
    private Long id;
    private Long subjectId;
    private String type;          // 单选题/多选题/填空题/解答题/判断题
    private Integer difficulty;   // 1~5
    private Double coefficient;   // 难度系数 0~1
    private String stem;
    private String kpNames;       // 知识点（逗号分隔）
    private String options;       // JSON [{l,v}]
    private String answer;
    private String analysis;      // JSON {brief,solve,comment}
    private String author;        // 作者/生成方（AI 场景如 "AI:MOCK"）
    private Integer status;
}
