package com.examforge.practice.logic;

/** 错题本联动差分纯规则（e 卷通考后诊断闭环，docs/26 F-XKW-12；docs/15 PR-4 同口径；可脱离 Spring 单测） */
public final class WrongBookRules {

    /** 联动动作：MARK_WRONG 入错题本（wrong_count+1、置未解决）｜ RESOLVE 解决既有错题 ｜ NONE 不动 */
    public enum SyncAction { MARK_WRONG, RESOLVE, NONE }

    private WrongBookRules() { }

    /**
     * 判分状态差分（旧 correct → 新 correct，1对 0错 NULL待批改）：
     * - 新值为 NULL（待批改）→ NONE：待批不触碰错题本；
     * - 新值错：仅"新进入错误态"（待批→错 / 对→错 改判）MARK_WRONG——维持错误态（错→错，扫描重导/重复批改）不重复计数，防 wrong_count 虚高；
     * - 新值对 → 一律 RESOLVE（幂等，跨作业/练习口径一致：该生最近一次作答已掌握即解决）。
     */
    public static SyncAction syncAction(Integer oldCorrect, Integer newCorrect) {
        if (newCorrect == null) return SyncAction.NONE;
        if (newCorrect == 0) {
            return (oldCorrect == null || oldCorrect == 1) ? SyncAction.MARK_WRONG : SyncAction.NONE;
        }
        return SyncAction.RESOLVE;
    }
}
