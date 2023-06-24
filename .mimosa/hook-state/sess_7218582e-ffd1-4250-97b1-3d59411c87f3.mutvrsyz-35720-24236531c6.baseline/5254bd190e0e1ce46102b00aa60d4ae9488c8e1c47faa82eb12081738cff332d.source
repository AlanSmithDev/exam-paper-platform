package com.examforge.school.controller;

import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.Result;
import com.examforge.school.service.SchoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 学校订阅超管端（SUPER_ADMIN/OP：B 端合同开通口径，docs/26 §7） */
@RestController
@RequestMapping("/api/v1/schools/admin")
@RequiredArgsConstructor
public class SchoolAdminController {

    private final SchoolService schoolService;

    /** 开通：{name, adminUserId, seatLimit?, months?} */
    @PostMapping
    public Result<Map<String, Object>> open(@RequestHeader(value = "X-User-Role", required = false) String role,
                                            @RequestBody Map<String, Object> body) {
        requireRole(role);
        return Result.ok(schoolService.open(body));
    }

    /** 全量列表（含有效状态/教师数/席位） */
    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestHeader(value = "X-User-Role", required = false) String role) {
        requireRole(role);
        return Result.ok(schoolService.list());
    }

    /** 续订：{months?}，缺省 12 个月 */
    @PostMapping("/{id}/renew")
    public Result<Map<String, Object>> renew(@RequestHeader(value = "X-User-Role", required = false) String role,
                                             @PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        requireRole(role);
        return Result.ok(schoolService.renew(id, body == null || body.get("months") == null
                ? null : Integer.valueOf(String.valueOf(body.get("months")))));
    }

    /** 关闭订阅 */
    @PostMapping("/{id}/close")
    public Result<Map<String, Object>> close(@RequestHeader(value = "X-User-Role", required = false) String role,
                                             @PathVariable Long id) {
        requireRole(role);
        return Result.ok(schoolService.close(id));
    }

    private void requireRole(String role) {
        if (!"SUPER_ADMIN".equals(role) && !"OP".equals(role)) {
            throw new BizException(Result.FORBIDDEN, "无权限");
        }
    }
}
