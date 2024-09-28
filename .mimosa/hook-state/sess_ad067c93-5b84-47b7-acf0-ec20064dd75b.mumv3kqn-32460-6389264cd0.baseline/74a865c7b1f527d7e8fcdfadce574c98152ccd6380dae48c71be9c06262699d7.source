package com.examforge.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.examforge.resource.domain.CreatorContract;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

public interface CreatorContractMapper extends BaseMapper<CreatorContract> {

    /** 当前生效合同：状态 ACTIVE 且在合同期内（start/end 可空=不限），同创作者多份取最新 */
    @Select("SELECT * FROM creator_contract WHERE creator_user_id = #{uid} AND status = 'ACTIVE' "
            + "AND (start_at IS NULL OR start_at <= #{now}) AND (end_at IS NULL OR end_at > #{now}) "
            + "ORDER BY id DESC LIMIT 1")
    CreatorContract selectActive(@Param("uid") Long uid, @Param("now") LocalDateTime now);
}
