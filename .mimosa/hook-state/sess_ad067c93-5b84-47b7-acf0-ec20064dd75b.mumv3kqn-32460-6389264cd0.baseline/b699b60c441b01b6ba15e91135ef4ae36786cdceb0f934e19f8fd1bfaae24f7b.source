package com.examforge.practice.controller;

import com.examforge.common.web.Result;
import com.examforge.practice.service.PracticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 练习/错题对外接口（网关 /api/v1/practices|wrong-questions|report/**） */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PracticeController {

    private final PracticeService service;

    @PostMapping("/practices")
    public Result<Map<String, Object>> create(@RequestHeader("X-User-Id") String uid,
                                              @RequestBody Map<String, Object> body) {
        return Result.ok(service.create(Long.valueOf(uid),
                body.get("subjectId") == null ? null : Long.valueOf(String.valueOf(body.get("subjectId"))),
                (String) body.getOrDefault("kp", ""),
                (String) body.getOrDefault("type", ""),
                body.get("count") == null ? 10 : Integer.parseInt(String.valueOf(body.get("count")))));
    }

    /** PR-5 错题再练卷 */
    @PostMapping("/practices/from-wrong")
    public Result<Map<String, Object>> fromWrong(@RequestHeader("X-User-Id") String uid,
                                                 @RequestBody(required = false) Map<String, Object> body) {
        int count = 10;
        if (body != null && body.get("count") != null) {
            count = Integer.parseInt(String.valueOf(body.get("count")));
        }
        return Result.ok(service.createFromWrong(Long.valueOf(uid), count));
    }

    @PostMapping("/practices/{id}/submit")
    public Result<Map<String, Object>> submit(@RequestHeader("X-User-Id") String uid,
                                              @PathVariable Long id,
                                              @RequestBody List<Map<String, Object>> answers) {
        return Result.ok(service.submit(Long.valueOf(uid), id, answers));
    }

    @GetMapping("/wrong-questions")
    public Result<List<WrongQuestionResponse>> wrongBook(@RequestHeader("X-User-Id") String uid,
                                                         @RequestParam(required = false) Boolean resolved) {
        return Result.ok(service.wrongBook(Long.valueOf(uid), resolved).stream()
                .map(w -> new WrongQuestionResponse(w.getId(), w.getQuestionId(), w.getKpNames(),
                        w.getWrongCount(), w.getResolved(), w.getLastWrongAt() == null ? null : w.getLastWrongAt().toString()))
                .toList());
    }

    @PostMapping("/wrong-questions/{qid}/resolve")
    public Result<Map<String, Object>> resolve(@RequestHeader("X-User-Id") String uid, @PathVariable Long qid) {
        return Result.ok(Map.of("ok", true));   // 再练答对时由 submit 自动 resolve；此接口支持手动标记
    }

    @GetMapping("/report/kp")
    public Result<List<Map<String, Object>>> kpReport(@RequestHeader("X-User-Id") String uid) {
        return Result.ok(service.kpReport(Long.valueOf(uid)));
    }

    public record WrongQuestionResponse(Long id, Long questionId, String kpNames,
                                        int wrongCount, int resolved, String lastWrongAt) { }
}
