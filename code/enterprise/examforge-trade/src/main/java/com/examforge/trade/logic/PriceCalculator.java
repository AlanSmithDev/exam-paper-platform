package com.examforge.trade.logic;

/** 纯逻辑：下载计费规则（docs/14 §3 P-3 / §6 D-1），无任何数据库依赖，单测覆盖 */
public class PriceCalculator {

    /** 按题量计价（分）：1-10题=100，11-20=200，21-50=400，>50=600 */
    public static int paperPriceCents(int questionCount) {
        if (questionCount <= 10) return 100;
        if (questionCount <= 20) return 200;
        if (questionCount <= 50) return 400;
        return 600;
    }

    public enum Mode { FREE, MEMBER, POINTS }

    /**
     * 判价：重复下载 → 会员 → 每日免费额度 → 点数
     * @param repeatWithin30d 是否 30 天内下载过同一卷
     * @param memberActive    会员是否有效
     * @param freeLeftToday   今日剩余免费额度
     * @param pointBalance    点数余额
     * @param questionCount   卷子题量
     */
    public static Decision decide(boolean repeatWithin30d, boolean memberActive,
                                  int freeLeftToday, int pointBalance, int questionCount) {
        if (repeatWithin30d) return new Decision(Mode.FREE, 0, "30天内重复下载不重复计费");
        if (memberActive) return new Decision(Mode.MEMBER, 0, "会员下载不限次");
        int price = paperPriceCents(questionCount);
        if (freeLeftToday > 0) return new Decision(Mode.FREE, 0, "免费额度（今日剩余 " + freeLeftToday + " 次）");
        if (pointBalance >= price) return new Decision(Mode.POINTS, price, "点数下载");
        return new Decision(Mode.POINTS, price, "点数不足：需要 " + price + " 点，余额 " + pointBalance);
    }

    public record Decision(Mode mode, int needPoints, String reason) {
        public boolean affordable() { return mode == Mode.FREE || mode == Mode.MEMBER || needPoints <= Integer.MAX_VALUE; }
    }
}
