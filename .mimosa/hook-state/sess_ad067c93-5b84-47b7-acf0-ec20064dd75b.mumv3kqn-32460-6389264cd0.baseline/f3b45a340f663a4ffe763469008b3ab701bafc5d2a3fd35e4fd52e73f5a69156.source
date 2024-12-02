package com.examforge.paper.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 试卷题目（sort 顺序 + score 分值） */
@Data
@TableName("paper_question")
public class PaperQuestion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long paperId;
    private Long questionId;
    private Integer sort;
    private Integer score;
}
