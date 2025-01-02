package com.examforge.practice.controller;

import com.examforge.common.web.Result;
import com.examforge.practice.domain.ScanRecord;
import com.examforge.practice.service.ScanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** 扫描阅卷接口（e 卷通二阶段：上传/列表/预览/名单/识别落账，docs/26 §7） */
@RestController
@RequestMapping("/api/v1/assignments")
@RequiredArgsConstructor
public class ScanController {

    private final ScanService scanService;

    /** 上传学生扫描件（multipart，≤10MB，jpg/jpeg/png/pdf；教师本人作业+名单校验） */
    @PostMapping("/{id}/students/{studentId}/scan")
    public Result<Map<String, Object>> upload(@RequestHeader("X-User-Id") String uid, @PathVariable Long id,
                                              @PathVariable Long studentId,
                                              @RequestParam("file") MultipartFile file) {
        return Result.ok(scanService.upload(Long.valueOf(uid), id, studentId, file));
    }

    /** 扫描件列表（可按学生过滤） */
    @GetMapping("/{id}/scans")
    public Result<List<ScanRecord>> list(@RequestHeader("X-User-Id") String uid, @PathVariable Long id,
                                         @RequestParam(required = false) Long studentId) {
        return Result.ok(scanService.list(Long.valueOf(uid), id, studentId));
    }

    /** 扫描件文件流（教师预览对照） */
    @GetMapping("/{id}/scans/{scanId}/file")
    public ResponseEntity<byte[]> file(@RequestHeader("X-User-Id") String uid, @PathVariable Long id,
                                       @PathVariable Long scanId) {
        Map<String, Object> f = scanService.file(Long.valueOf(uid), id, scanId);
        return ResponseEntity.ok()
                .header("Content-Type", String.valueOf(f.get("mime")))
                .header("Content-Disposition", "inline; filename=\"" + f.get("fileName") + "\"")
                .body((byte[]) f.get("bytes"));
    }

    /** 批改名单（含每人扫描件数与作业状态） */
    @GetMapping("/{id}/students")
    public Result<List<Map<String, Object>>> roster(@RequestHeader("X-User-Id") String uid, @PathVariable Long id) {
        return Result.ok(scanService.roster(Long.valueOf(uid), id));
    }

    /** 识别结果落账（P3 AI 视觉钩子：MOCK 期人工转录后确认） */
    @PostMapping("/{id}/scans/{scanId}/recognize")
    public Result<Map<String, Object>> recognize(@RequestHeader("X-User-Id") String uid, @PathVariable Long id,
                                                 @PathVariable Long scanId,
                                                 @RequestBody(required = false) String ocrJson) {
        return Result.ok(scanService.recognize(Long.valueOf(uid), id, scanId, ocrJson));
    }

    /** AI 视觉识别（识别成功自动置 RECOGNIZED 并写入 ocr_json；未识别保持人工转录流程） */
    @PostMapping("/{id}/scans/{scanId}/ai-recognize")
    public Result<Map<String, Object>> aiRecognize(@RequestHeader("X-User-Id") String uid, @PathVariable Long id,
                                                   @PathVariable Long scanId) {
        return Result.ok(scanService.recognizeByAi(Long.valueOf(uid), id, scanId));
    }
}
