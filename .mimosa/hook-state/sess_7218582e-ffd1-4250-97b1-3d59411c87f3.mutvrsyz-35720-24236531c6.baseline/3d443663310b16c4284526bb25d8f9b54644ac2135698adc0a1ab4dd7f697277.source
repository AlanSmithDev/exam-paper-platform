package com.examforge.practice.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 错题本联动差分规则单测（docs/26 F-XKW-12 考后诊断闭环验收） */
class WrongBookRulesTest {

    @Test
    void 待批改_一律不触碰错题本() {
        assertEquals(WrongBookRules.SyncAction.NONE, WrongBookRules.syncAction(null, null));
        assertEquals(WrongBookRules.SyncAction.NONE, WrongBookRules.syncAction(0, null));
        assertEquals(WrongBookRules.SyncAction.NONE, WrongBookRules.syncAction(1, null));
    }

    @Test
    void 判错_仅新进入错误态才入本_维持错误态不重复计数() {
        // 待批→错（学生首次提交答错 / 扫描导入判错）：入本
        assertEquals(WrongBookRules.SyncAction.MARK_WRONG, WrongBookRules.syncAction(null, 0));
        // 对→错（教师改判/修正导入翻案）：重新入本
        assertEquals(WrongBookRules.SyncAction.MARK_WRONG, WrongBookRules.syncAction(1, 0));
        // 错→错（扫描件重复导入/重复批改同一错答）：不动作，防 wrong_count 虚高
        assertEquals(WrongBookRules.SyncAction.NONE, WrongBookRules.syncAction(0, 0));
    }

    @Test
    void 判对_一律解决既有错题() {
        // 错→对（重做/修正导入答对）：解决
        assertEquals(WrongBookRules.SyncAction.RESOLVE, WrongBookRules.syncAction(0, 1));
        // 待批→对（客观题首判正确）：也解决此前其他来源（练习/其他作业）留下的未解决错题
        assertEquals(WrongBookRules.SyncAction.RESOLVE, WrongBookRules.syncAction(null, 1));
        // 对→对（重复批改）：幂等，仍走解决（无错题时为空操作）
        assertEquals(WrongBookRules.SyncAction.RESOLVE, WrongBookRules.syncAction(1, 1));
    }
}
