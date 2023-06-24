package com.examforge.question.controller;

import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.Result;
import com.examforge.question.domain.Question;
import com.examforge.question.mapper.QuestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 教师录题投稿（docs/02 4.4 P1）：
 * 教师提交题目 → status=1 审核中 → EDITOR 终审（QuestionAuditController）→ 上架。
 * 作者署名链从网关透传的 X-User-Nickname 获取；aigc 由提交端标记。
 */
@RestController
@RequestMapping("/api/v1/questions")
@RequiredArgsConstructor
public class ContributeController {

    private final QuestionMapper mapper;

    @PostMapping("/contribute")
    public Result<Map<String, Object>> contribute(@RequestHeader("X-User-Id") String uid,
                                                  @RequestHeader(value = "X-User-Nickname", required = false) String nickname,
                                                  @RequestBody Question body) {
        if (body.getStem() == null || body.getStem().isBlank() || body.getAnswer() == null || body.getAnswer().isBlank()) {
            return Result.fail(Result.BAD_REQUEST, "题干与答案必填");
        }
        if (body.getSubjectId() == null) return Result.fail(Result.BAD_REQUEST, "subjectId 必填");
        body.setId(null);
        body.setStatus(1);                       // 一律进审核队列
        body.setAuthor(nickname == null || nickname.isBlank() ? "教师#" + uid : nickname);
        if (body.getAigc() == null) body.setAigc(0);
        body.setUseCount(0);
        body.setCreatedAt(LocalDateTime.now());
        mapper.insert(body);
        return Result.ok(Map.of("id", body.getId(), "status", 1,
                "msg", "已提交审核，通过后将上架并署名"));
    }
}
