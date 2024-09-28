package com.examforge.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.examforge.trade.domain.CdkBatch;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface CdkBatchMapper extends BaseMapper<CdkBatch> {

    /** 原子计数：redeemed < total 才自增，防超兑（并发兑换兜底，与码级条件更新双保险） */
    @Update("UPDATE cdk_batch SET redeemed = redeemed + 1 WHERE id = #{batchId} AND redeemed < total")
    int incrRedeemed(@Param("batchId") Long batchId);
}
