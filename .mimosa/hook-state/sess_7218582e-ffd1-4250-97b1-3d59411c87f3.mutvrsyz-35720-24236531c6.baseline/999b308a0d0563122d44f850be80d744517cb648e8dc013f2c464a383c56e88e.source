package com.examforge.user.logic;

import java.time.LocalDateTime;

/** 登录防爆破纯规则（docs/20 §6 等保二级"登录防爆破"：同源失败 5 次/5min 临时锁定 15min；可脱离 Spring 单测） */
public final class LoginGuardRules {

    /** 锁定阈值：窗口内失败次数 */
    public static final int FAIL_LIMIT = 5;
    /** 滑动窗口（分钟） */
    public static final int WINDOW_MINUTES = 5;
    /** 锁定时长（分钟，自窗口内最后一次失败起算） */
    public static final int LOCK_MINUTES = 15;

    private LoginGuardRules() { }

    /**
     * 锁定判定：窗口内失败次数 ≥ FAIL_LIMIT → 锁定至 lastFailAt + LOCK_MINUTES；否则 null（未锁定）。
     * recentFails 为窗口（now-WINDOW_MINUTES, now] 内同源失败次数；lastFailAt 为窗口内最后一次失败时间。
     */
    public static LocalDateTime lockedUntil(long recentFails, LocalDateTime lastFailAt, LocalDateTime now) {
        if (recentFails < FAIL_LIMIT || lastFailAt == null) return null;
        LocalDateTime until = lastFailAt.plusMinutes(LOCK_MINUTES);
        return until.isAfter(now) ? until : null;
    }

    /** 锁定剩余秒数（给用户提示用，向上取整；未锁定返回 0） */
    public static long lockRemainSeconds(LocalDateTime lockedUntil, LocalDateTime now) {
        if (lockedUntil == null || !lockedUntil.isAfter(now)) return 0;
        return java.time.Duration.between(now, lockedUntil).toSeconds() + 1;
    }

    /** 窗口起点：统计 recentFails 用的下界（开区间由调用方 SQL > 处理） */
    public static LocalDateTime windowStart(LocalDateTime now) {
        return now.minusMinutes(WINDOW_MINUTES);
    }
}
