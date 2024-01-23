package com.examforge.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.examforge.resource.domain.CreatorEarning;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface CreatorEarningMapper extends BaseMapper<CreatorEarning> {

    /** 累计分成（合计按全部流水口径，不随分页截断） */
    @Select("SELECT COALESCE(SUM(share_cents), 0) FROM creator_earning WHERE creator_user_id = #{uid}")
    long sumShare(@Param("uid") Long uid);

    /** 月收入榜（docs/26 §6）：按自然月聚合 creator_earning，取分成 TOP N */
    @Select("SELECT creator_user_id AS creatorUserId, COALESCE(SUM(share_cents), 0) AS shareCents, COUNT(*) AS cnt "
            + "FROM creator_earning WHERE created_at >= #{start} AND created_at < #{end} "
            + "GROUP BY creator_user_id ORDER BY shareCents DESC LIMIT #{top}")
    List<Map<String, Object>> monthBoard(@Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end,
                                         @Param("top") int top);
}
