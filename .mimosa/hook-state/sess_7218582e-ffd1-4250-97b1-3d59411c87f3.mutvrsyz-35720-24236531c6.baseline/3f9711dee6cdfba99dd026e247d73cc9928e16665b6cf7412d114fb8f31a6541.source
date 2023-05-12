package com.examforge.practice.controller;

import com.examforge.common.web.Result;
import com.examforge.practice.domain.Assignment;
import com.examforge.practice.service.AssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 作业域对外接口（e 卷通首期：教师布置/学生作答/班级报告，docs/26 F-XKW-12） */
@RestController
@RequestMapping("/api/v1/assignments")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;

    // ---------- 教师端 ----------

    /** 布置作业（草稿）：{title, subjectId?, questionIds:[], deadlineHours?} */
    @PostMapping
    public Result<Map<String, Object>> create(@RequestHeader("X-User-Id") String uid,
                                              @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> raw = (List<Object>) body.getOrDefault("questionIds", List.of());
        return Result.ok(assignmentService.create(Long.valueOf(uid),
                String.valueOf(body.getOrDefault("title", "")),
                body.get("subjectId") == null ? null : Long.valueOf(String.valueOf(body.get("subjectId"))),
                raw.stream().map(x -> Long.valueOf(String.valueOf(x))).toList(),
                body.get("deadlineHours") == null ? null : Integer.valueOf(String.valueOf(body.get("deadlineHours")))));
    }

    /** 发布（草稿→已发布） */
    @PostMapping("/{id}/publish")
    public Result<Map<String, Object>> publish(@RequestHeader("X-User-Id") String uid, @PathVariable Long id) {
        return Result.ok(assignmentService.publish(Long.valueOf(uid), id));
    }

    /** 关闭/作废 */
    @PostMapping("/{id}/close")
    public Result<Map<String, Object>> close(@RequestHeader("X-User-Id") String uid, @PathVariable Long id) {
        return Result.ok(assignmentService.close(Long.valueOf(uid), id));
    }

    /** 点名：{studentIds:[]}（幂等） */
    @PostMapping("/{id}/students")
    public Result<Map<String, Object>> assign(@RequestHeader("X-User-Id") String uid, @PathVariable Long id,
                                              @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> raw = (List<Object>) body.getOrDefault("studentIds", List.of());
        return Result.ok(assignmentService.assign(Long.valueOf(uid), id,
                raw.stream().map(x -> Long.valueOf(String.valueOf(x))).toList()));
    }

    /** 我布置的作业 */
    @GetMapping("/mine")
    public Result<List<Assignment>> mine(@RequestHeader("X-User-Id") String uid) {
        return Result.ok(assignmentService.myTeaching(Long.valueOf(uid)));
    }

    /** 班级报告：名单概览/逐题正确率/薄弱知识点 */
    @GetMapping("/{id}/report")
    public Result<Map<String, Object>> report(@RequestHeader("X-User-Id") String uid, @PathVariable Long id) {
        return Result.ok(assignmentService.classReport(Long.valueOf(uid), id));
    }

    /** 学生个体学情报告（考后诊断）：教师查学生（须为其任一作业布置者） */
    @GetMapping("/students/{studentId}/report")
    public Result<Map<String, Object>> studentReport(@RequestHeader("X-User-Id") String uid,
                                                     @PathVariable Long studentId) {
        return Result.ok(assignmentService.studentReport(Long.valueOf(uid), studentId));
    }

    /** 我的学情（学生自查：跨作业正确率/薄弱知识点/成绩趋势/错题本 TOP） */
    @GetMapping("/my/report")
    public Result<Map<String, Object>> myReport(@RequestHeader("X-User-Id") String uid) {
        return Result.ok(assignmentService.studentReport(Long.valueOf(uid), Long.valueOf(uid)));
    }

    /** 作业答题卡（一键生成：客观题涂卡网格+主观题作答区，docs/26 §7） */
    @GetMapping("/{id}/answer-sheet")
    public Result<Map<String, Object>> answerSheet(@RequestHeader("X-User-Id") String uid, @PathVariable Long id) {
        return Result.ok(assignmentService.answerSheet(Long.valueOf(uid), id));
    }

    /** 批改解答题：{items:[{questionId, correct:"1"/"0", score?}]} */
    @PostMapping("/{id}/students/{studentId}/grade")
    public Result<Map<String, Object>> grade(@RequestHeader("X-User-Id") String uid, @PathVariable Long id,
                                             @PathVariable Long studentId, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> raw = (List<Object>) body.getOrDefault("items", List.of());
        return Result.ok(assignmentService.grade(Long.valueOf(uid), id, studentId,
                raw.stream().map(x -> (Map<String, Object>) x).toList()));
    }

    // ---------- 学生端 ----------

    /** 我的作业列表 */
    @GetMapping("/my")
    public Result<List<Map<String, Object>>> myAssignments(@RequestHeader("X-User-Id") String uid) {
        return Result.ok(assignmentService.studentView(Long.valueOf(uid)));
    }

    /** 取作业题目（脱敏：不含答案/解析；answerable=可作答，已提交/已关闭仍可回看题目） */
    @GetMapping("/{id}/questions")
    public Result<Map<String, Object>> questions(@RequestHeader("X-User-Id") String uid, @PathVariable Long id) {
        return Result.ok(assignmentService.questionsForStudent(Long.valueOf(uid), id));
    }

    /** 提交作答：[{questionId, answer, durationMs?}] */
    @PostMapping("/{id}/submit")
    public Result<Map<String, Object>> submit(@RequestHeader("X-User-Id") String uid, @PathVariable Long id,
                                              @RequestBody List<Map<String, Object>> answers) {
        return Result.ok(assignmentService.submit(Long.valueOf(uid), id, answers));
    }
}
