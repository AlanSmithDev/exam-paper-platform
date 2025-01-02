package com.examforge.resource.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.Result;
import com.examforge.resource.domain.CopyrightAppeal;
import com.examforge.resource.domain.ResourceItem;
import com.examforge.resource.logic.ResourceRules;
import com.examforge.resource.mapper.CopyrightAppealMapper;
import com.examforge.resource.mapper.ResourceItemMapper;
import com.examforge.resource.service.ResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 资源中心管理端：上架审核 / 资源配置 / 版权工单处理（SUPER_ADMIN/OP/EDITOR，docs/26 F-XKW-14） */
@RestController
@RequestMapping("/api/v1/resources/admin")
@RequiredArgsConstructor
public class ResourceAdminController {

    private final ResourceItemMapper itemMapper;
    private final CopyrightAppealMapper appealMapper;
    private final ResourceService resourceService;

    @GetMapping("/items")
    public Result<Page<ResourceItem>> list(@RequestHeader(value = "X-User-Role", required = false) String role,
                                           @RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "20") long size,
                                           @RequestParam(required = false) Integer status) {
        requireRole(role);
        return Result.ok(itemMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ResourceItem>()
                        .eq(status != null, ResourceItem::getStatus, status)
                        .orderByDesc(ResourceItem::getId)));
    }

    @PostMapping("/items")
    public Result<Map<String, Object>> create(@RequestHeader(value = "X-User-Role", required = false) String role,
                                              @RequestBody ResourceItem r) {
        requireRole(role);
        if (r.getTitle() == null || r.getStage() == null || r.getCategory() == null) {
            return Result.fail(Result.BAD_REQUEST, "标题/学段/类别必填");
        }
        if (r.getLevel() == null) r.setLevel(ResourceRules.LEVEL_NORMAL);
        try {
            ResourceRules.validatePricing(r.getLevel(), r.getPriceCents());
        } catch (IllegalArgumentException e) {
            return Result.fail(Result.BAD_REQUEST, e.getMessage());
        }
        if (r.getPreviewFreePct() == null) r.setPreviewFreePct(33);
        if (r.getPriceCents() == null) r.setPriceCents(200);
        r.setStatus(0);   // 新建一律进待审
        itemMapper.insert(r);
        return Result.ok(Map.of("id", r.getId()));
    }

    @PutMapping("/items/{id}/status")
    public Result<Map<String, Object>> status(@RequestHeader(value = "X-User-Role", required = false) String role,
                                              @PathVariable Long id, @RequestBody Map<String, Object> body) {
        requireRole(role);
        ResourceItem r = new ResourceItem();
        r.setId(id);
        r.setStatus(Integer.parseInt(String.valueOf(body.get("status"))));
        itemMapper.updateById(r);
        return Result.ok(Map.of("ok", true));
    }

    // ---------- 版权工单 ----------

    @GetMapping("/appeals")
    public Result<Page<CopyrightAppeal>> appeals(@RequestHeader(value = "X-User-Role", required = false) String role,
                                                 @RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "20") long size,
                                                 @RequestParam(required = false) String status) {
        requireRole(role);
        return Result.ok(appealMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<CopyrightAppeal>()
                        .eq(status != null && !status.isBlank(), CopyrightAppeal::getStatus, status)
                        .orderByDesc(CopyrightAppeal::getId)));
    }

    @PutMapping("/appeals/{id}/handle")
    public Result<Map<String, Object>> handle(@RequestHeader(value = "X-User-Role", required = false) String role,
                                              @PathVariable Long id, @RequestBody Map<String, Object> body) {
        requireRole(role);
        return Result.ok(resourceService.handleAppeal(id, role,
                String.valueOf(body.getOrDefault("action", "")),
                String.valueOf(body.getOrDefault("remark", ""))));
    }

    private void requireRole(String role) {
        if (!"SUPER_ADMIN".equals(role) && !"OP".equals(role) && !"EDITOR".equals(role)) {
            throw new BizException(Result.FORBIDDEN, "无权限");
        }
    }
}
