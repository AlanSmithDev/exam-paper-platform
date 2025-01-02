package com.examforge.resource.logic;

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
}
