package com.examforge.trade.logic;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 用户增长域纯规则：CDK 码格式/奖励参数校验/任务周期（可脱离 Spring 单测） */
public final class GrowthRules {

    /** 去歧义字符集：剔除 0/O/1/I/L，避免用户抄错 */
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final SecureRandom RNG = new SecureRandom();

    public static final String TYPE_MEMBER_DAYS = "MEMBER_DAYS";
    public static final String TYPE_POINTS = "POINTS";
    public static final String TYPE_COUPON = "COUPON";

    private GrowthRules() { }

    /** 生成一张 12 位码，展示格式 XXXX-XXXX-XXXX */
    public static String generateCode() {
        StringBuilder sb = new StringBuilder(14);
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i % 4 == 0) sb.append('-');
            sb.append(ALPHABET.charAt(RNG.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** 归一化用户输入：去空格/横线，转大写 */
    public static String normalize(String raw) {
        return raw == null ? "" : raw.replaceAll("[\\s-]+", "").toUpperCase();
    }

    /** 格式校验：归一化后必须 12 位且全部在去歧义字符集内 */
    public static boolean validCode(String normalized) {
        if (normalized == null || normalized.length() != 12) return false;
        for (char c : normalized.toCharArray()) {
            if (ALPHABET.indexOf(c) < 0) return false;
        }
        return true;
    }

    /** 任务周期：每日任务=当天 yyyymmdd，一次性=LIFETIME */
    public static String periodOf(boolean daily, LocalDate date) {
        return daily ? date.format(DateTimeFormatter.BASIC_ISO_DATE) : "LIFETIME";
    }

    /** 批次参数校验，非法抛 IllegalArgumentException（message 直接面向运营） */
    public static void validateBatch(String rewardType, Integer days, Integer points, Long templateId, Integer total) {
        int t = total == null ? 0 : total;
        if (t < 1 || t > 5000) throw new IllegalArgumentException("张数须在 1~5000 之间");
        switch (rewardType == null ? "" : rewardType) {
            case TYPE_MEMBER_DAYS -> {
                if (days == null || days < 1 || days > 366) throw new IllegalArgumentException("会员天数须在 1~366 之间");
            }
            case TYPE_POINTS -> {
                if (points == null || points < 1 || points > 100000) throw new IllegalArgumentException("点数须在 1~100000 之间");
            }
            case TYPE_COUPON -> {
                if (templateId == null || templateId <= 0) throw new IllegalArgumentException("必须关联券模板");
            }
            default -> throw new IllegalArgumentException("奖励类型非法（MEMBER_DAYS/POINTS/COUPON）");
        }
    }

    /** 批次是否可兑换（停用/过期统一判定，service 层配合条件更新使用） */
    public static boolean redeemable(Integer status, LocalDateTime expireTime, LocalDateTime now) {
        if (status == null || status != 1) return false;
        return expireTime == null || !expireTime.isBefore(now);
    }

    public static boolean redeemable(CdkBatchView batch, LocalDateTime now) {
        return batch != null && redeemable(batch.status(), batch.expireTime(), now);
    }

    /** 最小视图接口，便于单测构造，不必引入 MyBatis 实体 */
    public interface CdkBatchView {
        Integer status();
        LocalDateTime expireTime();
    }
}
