package com.examforge.resource.logic;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 资源域纯规则：预览页数/下载判价/申诉状态机（docs/26 F-XKW-01/02/14，可脱离 Spring 单测） */
public final class ResourceRules {

    public static final String LEVEL_FREE = "FREE";
    public static final String LEVEL_NORMAL = "NORMAL";
    public static final String LEVEL_SPECIAL = "SPECIAL";
    public static final String LEVEL_BOUTIQUE = "BOUTIQUE";

    public static final String APPEAL_OPEN = "OPEN";
    public static final String APPEAL_RESOLVED = "RESOLVED";
    public static final String APPEAL_REJECTED = "REJECTED";
    public static final String APPEAL_WITHDRAWN = "WITHDRAWN";

    public static final String SETTLE_MONTHLY = "MONTHLY";
    public static final String CONTRACT_ACTIVE = "ACTIVE";
    public static final String CONTRACT_ENDED = "ENDED";

    private ResourceRules() { }

    /** 免费预览页数：学科网实测 33% 规则，至少 1 页（0 页资源不可预览） */
    public static int previewFreePages(int totalPages, int freePct) {
        if (totalPages <= 0) return 0;
        int pct = Math.min(100, Math.max(1, freePct));
        return Math.max(1, (int) Math.floor(totalPages * pct / 100.0));
    }

    public static boolean freeLevel(String level) {
        return LEVEL_FREE.equals(level);
    }

    /** 资源等级→价格校验：免费档价格必须为 0，其余档位 >0 */
    public static void validatePricing(String level, Integer priceCents) {
        int price = priceCents == null ? 0 : priceCents;
        if (freeLevel(level)) {
            if (price != 0) throw new IllegalArgumentException("免费资源价格必须为 0");
        } else {
            if (price < 1 || price > 100000) throw new IllegalArgumentException("收费资源价格须在 1~100000 分之间");
        }
    }

    public enum Mode { FREE, POINTS, INSUFFICIENT }

    /** 资源篮单件结算判定结果：未上架跳过 / 已购跳过 / 免费 / 计费（docs/26 F-XKW-03 批量结算） */
    public enum BasketMode { FREE, POINTS, OWNED, SKIP }

    /** 资源篮单件结算判定：未上架 SKIP；已购 OWNED；免费档 FREE；其余 POINTS 计费 */
    public static BasketMode basketMode(String level, boolean owned, boolean onShelf) {
        if (!onShelf) return BasketMode.SKIP;
        if (owned) return BasketMode.OWNED;
        return freeLevel(level) ? BasketMode.FREE : BasketMode.POINTS;
    }

    /** 下载判价：免费档/已购 → FREE；余额够 → POINTS；否则 INSUFFICIENT（与 docs/14 D 判价序对齐的精简版） */
    public static Mode decide(String level, boolean owned, int pointBalance, int priceCents) {
        if (freeLevel(level) || owned) return Mode.FREE;
        return pointBalance >= priceCents ? Mode.POINTS : Mode.INSUFFICIENT;
    }

    /** 创作者分成：分成点数 = floor(实收 × 比例/100)，比例夹取 0~100（运营可配，docs/26 §6）；价格非负由 validatePricing 保证 */
    public static int shareCents(int priceCents, int ratePct) {
        int pct = Math.min(100, Math.max(0, ratePct));
        return priceCents * pct / 100;
    }

    /** 申诉状态机：OPEN 为唯一可迁出态，终态不可再变 */
    public static boolean canTransition(String from, String to) {
        if (APPEAL_OPEN.equals(from)) {
            return Set.of(APPEAL_RESOLVED, APPEAL_REJECTED, APPEAL_WITHDRAWN).contains(to);
        }
        return false;
    }

    public static void mustTransition(String from, String to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("非法申诉状态跃迁: " + from + " -> " + to);
        }
    }

    /** 结算/榜单月份解析：month 须为 yyyy-MM，空则用 fallback；非法格式抛 IllegalArgumentException（docs/26 §6 P3） */
    public static YearMonth parseMonth(String month, YearMonth fallback) {
        if (month == null || month.isBlank()) return fallback;
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("month 须为 yyyy-MM 格式");
        }
    }

    /** 签约合同当前有效：状态 ACTIVE 且在合同期内（start/end 可空=不限） */
    public static boolean contractActive(String status, LocalDateTime startAt, LocalDateTime endAt, LocalDateTime now) {
        return CONTRACT_ACTIVE.equals(status)
                && (startAt == null || !startAt.isAfter(now))
                && (endAt == null || endAt.isAfter(now));
    }

    /** 分成比例：签约比例优先，否则全局默认；均夹取 0~100（docs/26 §6 签约比例覆盖） */
    public static int resolveSharePct(Integer contractRatePct, int globalPct) {
        int pct = contractRatePct == null ? globalPct : contractRatePct;
        return Math.min(100, Math.max(0, pct));
    }

    /** 签约合同参数校验：比例必填且 0~100 */
    public static void validateContractRate(Integer ratePct) {
        if (ratePct == null || ratePct < 0 || ratePct > 100) {
            throw new IllegalArgumentException("签约分成比例须在 0~100 之间");
        }
    }

    // ---------- 版权工单 SLA 看板（T-26c 收尾，docs/26 F-XKW-14：异议申诉+下架 SLA） ----------

    /** 工单 SLA 统计输入行 */
    public record SlaRow(String status, LocalDateTime createdAt, LocalDateTime resolvedAt) { }

    /** 版权工单 SLA 统计：总数/待处理/已处理/平均处理时长(小时)/超时未处理数/超时率%（超时=OPEN 超 slaHours 未处理） */
    public static Map<String, Object> appealSlaStats(List<SlaRow> rows, LocalDateTime now, int slaHours) {
        int total = rows.size(), open = 0, closed = 0, overdueOpen = 0, resolveCount = 0;
        double resolveHoursSum = 0;
        for (SlaRow r : rows) {
            if (APPEAL_OPEN.equals(r.status())) {
                open++;
                if (r.createdAt() != null && r.createdAt().isBefore(now.minusHours(slaHours))) overdueOpen++;
            } else {
                closed++;
                if (r.resolvedAt() != null && r.createdAt() != null) {
                    resolveHoursSum += Duration.between(r.createdAt(), r.resolvedAt()).toMinutes() / 60.0;
                    resolveCount++;
                }
            }
        }
        double avgResolveHours = resolveCount == 0 ? 0 : Math.round(resolveHoursSum / resolveCount * 10) / 10.0;
        double overdueRatePct = open == 0 ? 0 : Math.round(overdueOpen * 1000.0 / open) / 10.0;
        return Map.of("total", total, "open", open, "closed", closed,
                "avgResolveHours", avgResolveHours, "overdueOpen", overdueOpen,
                "overdueRatePct", overdueRatePct, "slaHours", slaHours);
    }
}
