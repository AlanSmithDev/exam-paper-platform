package com.examforge.practice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.examforge.practice.domain.Assignment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface AssignmentMapper extends BaseMapper<Assignment> {

    /** 发布/关闭的条件状态迁移（防并发重复操作；DRAFT→PUBLISHED、PUBLISHED→CLOSED） */
    @Update("UPDATE assignment SET status = #{to} WHERE id = #{id} AND status = #{from}")
    int transition(@Param("id") Long id, @Param("from") int from, @Param("to") int to);
}
