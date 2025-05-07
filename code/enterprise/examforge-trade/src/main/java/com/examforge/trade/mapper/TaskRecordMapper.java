package com.examforge.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.examforge.trade.domain.TaskRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface TaskRecordMapper extends BaseMapper<TaskRecord> {

    /** 唯一索引(user_id, task_key, period)幂等：重复完成返回 0 */
    @Insert("INSERT INTO task_record(user_id, task_key, period) VALUES(#{userId}, #{taskKey}, #{period}) " +
            "ON DUPLICATE KEY UPDATE id = id")
    int insertIgnore(@Param("userId") Long userId, @Param("taskKey") String taskKey, @Param("period") String period);
}
