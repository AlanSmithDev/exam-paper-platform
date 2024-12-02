package com.examforge.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.examforge.admin.domain.Ad;
import com.examforge.admin.domain.AuditLog;
import com.examforge.admin.mapper.AdMapper;
import com.examforge.admin.mapper.AuditLogMapper;
import com.examforge.admin.security.UrlSafetyChecker;
import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 广告位管理 + 审计（超管/运营角色，网关透传 X-User-Role / X-User-Id）。
 * 全部写操作落 audit_log；素材 URL 经 UrlSafetyChecker 校验。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AdAdminController {

    private static final List<String> POSITIONS = List.of(
            "home_hero", "home_banner", "sidebar_teacher", "sidebar_student",
            "list_inline", "detail_footer", "login_promo");

    private final AdMapper adMapper;
    private final AuditLogMapper auditMapper;

    // ---------- 前台公开读取 ----------
    @GetMapping("/ads")
    public Result<Map<String, Object>> publicAds(@RequestParam String position) {
        if (!POSITIONS.contains(position)) return Result.fail(Result.BAD_REQUEST, "position 非法");
        List<Ad> list = adMapper.selectList(new LambdaQueryWrapper<Ad>()
                .eq(Ad::getPosition, position).eq(Ad::getStatus, 1).orderByAsc(Ad::getSort));
        return Result.ok(Map.of("position", position, "list", list));
    }

    // ---------- 后台管理 ----------
    @GetMapping("/admin/ads")
    public Result<Map<String, Object>> all() {
        return Result.ok(Map.of("list", adMapper.selectList(new LambdaQueryWrapper<Ad>()
                .orderByAsc(Ad::getPosition).orderByAsc(Ad::getSort))));
    }

    @PostMapping("/admin/ads")
    public Result<Map<String, Object>> create(@RequestBody Ad ad, HttpServletRequest req) {
        checkRole(req);
        UrlSafetyChecker.check(ad.getImageUrl());
        UrlSafetyChecker.check(ad.getLinkUrl());
        ad.setId(null);
        if (ad.getStatus() == null) ad.setStatus(1);
        ad.setCreatedAt(LocalDateTime.now());
        adMapper.insert(ad);
        audit(req, "AD_CREATE", Map.of("id", ad.getId(), "position", String.valueOf(ad.getPosition())));
        return Result.ok(Map.of("id", ad.getId()));
    }

    @PutMapping("/admin/ads/{id}")
    public Result<Map<String, Object>> update(@PathVariable Long id, @RequestBody Ad ad, HttpServletRequest req) {
        checkRole(req);
        UrlSafetyChecker.check(ad.getImageUrl());
        UrlSafetyChecker.check(ad.getLinkUrl());
        ad.setId(id);
        adMapper.updateById(ad);
        audit(req, "AD_UPDATE", Map.of("id", id));
        return Result.ok(Map.of("ok", true));
    }

    @DeleteMapping("/admin/ads/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Long id, HttpServletRequest req) {
        checkRole(req);
        adMapper.deleteById(id);
        audit(req, "AD_DELETE", Map.of("id", id));
        return Result.ok(Map.of("ok", true));
    }

    // ---------- 审计查询（仅超管） ----------
    @GetMapping("/admin/audit")
    public Result<Map<String, Object>> audit(@RequestHeader(value = "X-User-Role", required = false) String role,
                                             @RequestParam(defaultValue = "50") long limit) {
        if (!"SUPER_ADMIN".equals(role)) return Result.fail(Result.FORBIDDEN, "仅超级管理员可查看审计");
        Page<AuditLog> p = auditMapper.selectPage(new Page<>(1, Math.min(limit, 200)),
                new LambdaQueryWrapper<AuditLog>().orderByDesc(AuditLog::getId));
        return Result.ok(Map.of("list", p.getRecords()));
    }

    private void checkRole(HttpServletRequest req) {
        String role = req.getHeader("X-User-Role");
        if (!"SUPER_ADMIN".equals(role) && !"OP".equals(role)) {
            throw new BizException(Result.FORBIDDEN, "无权限执行该操作");
        }
    }

    private void audit(HttpServletRequest req, String action, Map<String, Object> detail) {
        AuditLog log = new AuditLog();
        log.setUserId(req.getHeader("X-User-Id") == null ? null : Long.valueOf(req.getHeader("X-User-Id")));
        log.setAction(action);
        log.setDetail(detail.toString());
        log.setIp(req.getHeader("X-Forwarded-For") == null ? req.getRemoteAddr() : req.getHeader("X-Forwarded-For"));
        log.setCreatedAt(LocalDateTime.now());
        auditMapper.insert(log);
    }
}
