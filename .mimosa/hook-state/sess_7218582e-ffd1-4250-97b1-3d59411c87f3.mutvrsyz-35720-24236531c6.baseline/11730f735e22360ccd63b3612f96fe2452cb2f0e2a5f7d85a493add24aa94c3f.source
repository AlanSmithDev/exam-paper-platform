package com.examforge.practice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.examforge.practice.domain.WrongQuestion;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface WrongQuestionMapper extends BaseMapper<WrongQuestion> {

    /** 答错入库：存在则累加并置未解决，否则插入（docs/15 PR-4） */
    @Update("INSERT INTO wrong_question(user_id, question_id, kp_names, wrong_count, resolved, last_wrong_at) " +
            "VALUES(#{userId}, #{questionId}, #{kpNames}, 1, 0, NOW()) " +
            "ON DUPLICATE KEY UPDATE wrong_count = wrong_count + 1, resolved = 0, last_wrong_at = NOW()")
    int upsertWrong(@Param("userId") Long userId, @Param("questionId") Long questionId, @Param("kpNames") String kpNames);
}
