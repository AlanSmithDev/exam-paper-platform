package com.examforge.trade.job;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.examforge.trade.domain.TradeOrder;
import com.examforge.trade.mapper.TradeOrderMapper;
import com.examforge.trade.logic.OrderRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 订单超时关单（docs/14 O-3）：每分钟扫描 CREATED 且创建超过 30 分钟的订单 → CLOSED。
 * 多实例安全：MySQL GET_LOCK 抢分布式锁，同一时刻仅一个实例执行；
 * 条件更新保证与支付回调并发时的安全（回调先到则此处更新 0 行）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutScheduler {

    private final TradeOrderMapper orderMapper;
    private final JdbcTemplate jdbcTemplate;

    @Scheduled(fixedDelay = 60_000)
    public void closeTimeoutOrders() {
        Boolean locked = jdbcTemplate.queryForObject("SELECT GET_LOCK('examforge:close-timeout', 0)", Boolean.class);
        if (!Boolean.TRUE.equals(locked)) return;   // 其他实例持锁，本轮跳过
        try {
            int closed = orderMapper.update(null, new LambdaUpdateWrapper<TradeOrder>()
                    .eq(TradeOrder::getStatus, OrderRules.CREATED)
                    .lt(TradeOrder::getCreatedAt, LocalDateTime.now().minusMinutes(30))
                    .set(TradeOrder::getStatus, OrderRules.CLOSED));
            if (closed > 0) log.info("超时关单 {} 笔", closed);
        } finally {
            jdbcTemplate.queryForObject("SELECT RELEASE_LOCK('examforge:close-timeout')", Boolean.class);
        }
    }
}

