package com.examforge.practice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.examforge.practice.domain.AssignmentStudent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface AssignmentStudentMapper extends BaseMapper<AssignmentStudent> {

    /** 点名幂等：唯一索引(assignment_id, student_id)，重复指派返回 0 */
    @Insert("INSERT INTO assignment_student(assignment_id, student_id) VALUES(#{assignmentId}, #{studentId}) " +
            "ON DUPLICATE KEY UPDATE id = id")
    int insertIgnore(@Param("assignmentId") Long assignmentId, @Param("studentId") Long studentId);
}
