package com.examforge.school.logic;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** 学校订阅纯规则（T-26h，docs/26 §7"学校订阅制：校管理员开通→教师全员享权益"；可脱离 Spring 单测） */
public final class SchoolRules {

    /** 批量添加成员单次上限（对齐内部用户批量契约 ≤100） */
    public static final int MAX_BATCH = 100;

    private SchoolRules() { }

    /** 订阅有效：状态 OPEN 且未到期（到期时间为空视为未生效） */
    public static boolean isActive(Integer status, LocalDateTime memberUntil, LocalDateTime now) {
        return status != null && status == 1
                && memberUntil != null && memberUntil.isAfter(now == null ? LocalDateTime.now() : now);
    }

    /** 席位校验：教师现有人数 < 席位上限方可再加；上限为 NULL 视为不限 */
    public static boolean canAddTeacher(long currentTeachers, Integer seatLimit) {
        return seatLimit == null || currentTeachers < seatLimit;
    }

    /** 批量成员 userId 归一化：去空、去重保序、截断到单次上限 */
    public static List<Long> normalizeBatch(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return List.of();
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long id : userIds) {
            if (id == null) continue;
            distinct.add(id);
            if (distinct.size() >= MAX_BATCH) break;
        }
        return new ArrayList<>(distinct);
    }

    /** 角色归一化：TEACHER/STUDENT 大小写兼容，空/非法默认 TEACHER（教师权益为本域核心口径） */
    public static String normalizeRole(String role) {
        if (role == null) return "TEACHER";
        return switch (role.trim().toUpperCase()) {
            case "STUDENT" -> "STUDENT";
            default -> "TEACHER";
        };
    }

    /** 续订：从"当前到期时间与现在的较晚者"起加 N 个月（未到期续订顺延，已到期从现在起算） */
    public static LocalDateTime renewUntil(LocalDateTime memberUntil, int months, LocalDateTime now) {
        if (months <= 0) months = 1;
        LocalDateTime base = memberUntil != null && memberUntil.isAfter(now) ? memberUntil : now;
        return base.plusMonths(months);
    }
}
