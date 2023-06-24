package com.examforge.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.examforge.trade.domain.SignRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

public interface SignRecordMapper extends BaseMapper<SignRecord> {

    /** 签到：唯一索引(user_id, sign_date) 防重复，重复插入返回 0 */
    @Insert("INSERT INTO sign_record(user_id, sign_date) VALUES(#{userId}, #{signDate}) ON DUPLICATE KEY UPDATE id = id")
    int insertIgnore(@Param("userId") Long userId, @Param("signDate") LocalDate signDate);
}
