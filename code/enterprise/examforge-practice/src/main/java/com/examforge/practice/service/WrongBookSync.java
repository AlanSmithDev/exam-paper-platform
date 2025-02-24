package com.examforge.practice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.examforge.practice.domain.WrongQuestion;
import com.examforge.practice.logic.WrongBookRules;
import com.examforge.practice.mapper.WrongQuestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 错题本联动（e 卷通考后诊断闭环，docs/26 F-XKW-12）：
 * 作业域三个判分落点（submit/importScan/grade）统一经 WrongBookRules 差分后调用，
 * 与练习线（PracticeService PR-4）同表同口径。
 */
@Component
@RequiredArgsConstructor
public class WrongBookSync {

    private final WrongQuestionMapper wrongMapper;

    /** 按差分结果联动：入错题本（带知识点供错题本展示/再练）或解决既有未解决错题 */
    public void sync(Long studentId, Long questionId, String kpNames, Integer oldCorrect, Integer newCorrect) {
        switch (WrongBookRules.syncAction(oldCorrect, newCorrect)) {
            case MARK_WRONG -> wrongMapper.upsertWrong(studentId, questionId, kpNames);
            case RESOLVE -> resolve(studentId, questionId);
            case NONE -> { }
        }
    }

    private void resolve(Long uid, Long qid) {
        WrongQuestion w = wrongMapper.selectOne(new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getUserId, uid).eq(WrongQuestion::getQuestionId, qid));
        if (w != null && w.getResolved() == 0) {
            w.setResolved(1);
            wrongMapper.updateById(w);
        }
    }

    /** 未解决错题 TOP（个体学情报告用）：按累计错误次数、最近出错时间倒序 */
    public java.util.List<WrongQuestion> wrongTop(Long userId, int limit) {
        return wrongMapper.selectList(new LambdaQueryWrapper<WrongQuestion>()
                .eq(WrongQuestion::getUserId, userId).eq(WrongQuestion::getResolved, 0)
                .orderByDesc(WrongQuestion::getWrongCount)
                .orderByDesc(WrongQuestion::getLastWrongAt)
                .last("LIMIT " + Math.max(1, Math.min(limit, 20))));
    }
}
