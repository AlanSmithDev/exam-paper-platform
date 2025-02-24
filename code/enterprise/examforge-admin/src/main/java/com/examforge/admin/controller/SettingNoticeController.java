package com.examforge.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.examforge.admin.domain.AuditLog;
import com.examforge.admin.domain.Notice;
import com.examforge.admin.domain.Setting;
import com.examforge.admin.mapper.AuditLogMapper;
import com.examforge.admin.mapper.NoticeMapper;
import com.examforge.admin.mapper.SettingMapper;
import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 系统设置 + 公告（前台公开读 / 超管写，写操作入审计） */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SettingNoticeController {

    private final SettingMapper settingMapper;
    private final NoticeMapper noticeMapper;
    private final AuditLogMapper auditMapper;

    // ---------- 前台公开 ----------
    @GetMapping("/settings")
    public Result<Map<String, String>> settings() {
        Map<String, String> map = new LinkedHashMap<>();
        for (Setting s : settingMapper.selectList(null)) map.put(s.getKey(), s.getValue());
        return Result.ok(map);
    }

    @GetMapping("/notices")
    public Result<List<Notice>> publicNotices() {
        return Result.ok(noticeMapper.selectList(new LambdaQueryWrapper<Notice>()
                .eq(Notice::getStatus, 1).orderByDesc(Notice::getId).last("LIMIT 5")));
    }

    // ---------- 超管 ----------
    @PutMapping("/admin/settings")
    public Result<Map<String, Object>> putSettings(@RequestBody Map<String, String> body, HttpServletRequest req) {
        requireSuper(req);
        int n = 0;
        for (var e : body.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank()) continue;
            Setting s = new Setting();
            s.setKey(e.getKey());
            s.setValue(e.getValue() == null ? "" : e.getValue());
            settingMapper.insert(s);
            n++;
        }
        audit(req, "SETTINGS_PUT", body.keySet().toString());
        return Result.ok(Map.of("updated", n));
    }

    /** 审计写入（超管/运营写操作统一留痕，docs/12 §2.8） */
    private void audit(HttpServletRequest req, String action, Object detail) {
        AuditLog log = new AuditLog();
        log.setUserId(req.getHeader("X-User-Id") == null ? null : Long.valueOf(req.getHeader("X-User-Id")));
        log.setAction(action);
        log.setDetail(String.valueOf(detail));
        log.setIp(req.getHeader("X-Forwarded-For") == null ? req.getRemoteAddr() : req.getHeader("X-Forwarded-For"));
        log.setCreatedAt(LocalDateTime.now());
        auditMapper.insert(log);
    }

    @GetMapping("/admin/notices")
    public Result<List<Notice>> allNotices() {
        return Result.ok(noticeMapper.selectList(new LambdaQueryWrapper<Notice>().orderByDesc(Notice::getId)));
    }

    @PostMapping("/admin/notices")
    public Result<Map<String, Object>> createNotice(@RequestBody Notice n, HttpServletRequest req) {
        requireRole(req, "SUPER_ADMIN", "OP");
        n.setId(null);
        if (n.getStatus() == null) n.setStatus(1);
        if (n.getAudience() == null) n.setAudience("ALL");
        n.setCreatedAt(LocalDateTime.now());
        noticeMapper.insert(n);
        audit(req, "NOTICE_CREATE", Map.of("id", n.getId()));
        return Result.ok(Map.of("id", n.getId()));
    }

    @DeleteMapping("/admin/notices/{id}")
    public Result<Map<String, Object>> deleteNotice(@PathVariable Long id, HttpServletRequest req) {
        requireRole(req, "SUPER_ADMIN", "OP");
        noticeMapper.deleteById(id);
        audit(req, "NOTICE_DELETE", Map.of("id", id));
        return Result.ok(Map.of("ok", true));
    }

    private void requireSuper(HttpServletRequest req) {
        if (!"SUPER_ADMIN".equals(req.getHeader("X-User-Role"))) {
            throw new BizException(Result.FORBIDDEN, "仅超级管理员可操作系统设置");
        }
    }

    private void requireRole(HttpServletRequest req, String... roles) {
        String role = req.getHeader("X-User-Role");
        for (String r : roles) if (r.equals(role)) return;
        throw new BizException(Result.FORBIDDEN, "无权限执行该操作");
    }
}
