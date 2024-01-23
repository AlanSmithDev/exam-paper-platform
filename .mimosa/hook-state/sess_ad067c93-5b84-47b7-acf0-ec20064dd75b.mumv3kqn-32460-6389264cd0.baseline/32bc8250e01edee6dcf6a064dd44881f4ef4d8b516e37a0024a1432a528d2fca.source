package com.examforge.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.examforge.resource.domain.CreatorSettlement;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface CreatorSettlementMapper extends BaseMapper<CreatorSettlement> {

    /** 结算窗口内有待补发行（credit_status=0）的创作者 */
    @Select("SELECT DISTINCT creator_user_id FROM creator_earning "
            + "WHERE credit_status = 0 AND created_at >= #{start} AND created_at < #{end}")
    List<Long> unsettledCreators(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 指定创作者在窗口内的待补发分成合计 */
    @Select("SELECT COALESCE(SUM(share_cents), 0) FROM creator_earning "
            + "WHERE creator_user_id = #{uid} AND credit_status = 0 AND created_at >= #{start} AND created_at < #{end}")
    long sumUnsettled(@Param("uid") Long uid, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 指定创作者在窗口内的待补发行数 */
    @Select("SELECT COUNT(*) FROM creator_earning "
            + "WHERE creator_user_id = #{uid} AND credit_status = 0 AND created_at >= #{start} AND created_at < #{end}")
    int countUnsettled(@Param("uid") Long uid, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
