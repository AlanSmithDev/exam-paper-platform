package com.examforge.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.examforge.trade.domain.CdkCode;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface CdkCodeMapper extends BaseMapper<CdkCode> {

    /** 兑换占坑：状态 0→1 条件更新，一码一用（并发同码只有一人成功） */
    @Update("UPDATE cdk_code SET status = 1, used_by = #{uid}, used_at = NOW() WHERE code = #{code} AND status = 0")
    int claim(@Param("code") String code, @Param("uid") Long uid);
}
