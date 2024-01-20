package com.examforge.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.examforge.trade.domain.PointAccount;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface PointAccountMapper extends BaseMapper<PointAccount> {

    /** 原子扣减：余额不足返回 0（防透支，docs/14 P-1） */
    @Update("UPDATE point_account SET balance = balance - #{amount}, version = version + 1 WHERE user_id = #{userId} AND balance >= #{amount}")
    int deduct(@Param("userId") Long userId, @Param("amount") int amount);

    /** 原子增加 */
    @Update("UPDATE point_account SET balance = balance + #{amount}, version = version + 1 WHERE user_id = #{userId}")
    int add(@Param("userId") Long userId, @Param("amount") int amount);
}
