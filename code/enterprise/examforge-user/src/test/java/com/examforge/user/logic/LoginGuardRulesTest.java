package com.examforge.user.logic;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** 登录防爆破纯规则单测（docs/20 §6 等保二级验收） */
class LoginGuardRulesTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 9, 30, 10, 0);

    @Test
    void 锁定判定_窗口失败达阈值即锁() {
        LocalDateTime lastFail = now.minusMinutes(1);
        assertEquals(now.plusMinutes(14), LoginGuardRules.lockedUntil(5, lastFail, now), "5 次失败→锁至最后一次+15min");
        assertEquals(now.plusMinutes(14), LoginGuardRules.lockedUntil(9, lastFail, now), "超阈值同样锁至+15min");
        assertNull(LoginGuardRules.lockedUntil(4, lastFail, now), "未达阈值不锁");
        assertNull(LoginGuardRules.lockedUntil(0, null, now));
        assertNull(LoginGuardRules.lockedUntil(5, null, now), "无失败时间记录不锁（数据异常防御）");
    }

    @Test
    void 锁定过期自动解除() {
        LocalDateTime lastFail = now.minusMinutes(20);
        assertNull(LoginGuardRules.lockedUntil(5, lastFail, now), "最后一次失败已过 15min→锁定自动解除");
    }

    @Test
    void 剩余秒数提示_向上取整() {
        LocalDateTime until = now.plusSeconds(90);
        assertEquals(91, LoginGuardRules.lockRemainSeconds(until, now));
        assertEquals(0, LoginGuardRules.lockRemainSeconds(null, now));
        assertEquals(0, LoginGuardRules.lockRemainSeconds(now.minusSeconds(1), now));
    }

    @Test
    void 窗口起点() {
        assertEquals(now.minusMinutes(5), LoginGuardRules.windowStart(now));
    }
}
