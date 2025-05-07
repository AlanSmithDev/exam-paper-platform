package com.examforge.trade.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.examforge.common.web.Result;
import com.examforge.trade.domain.CouponTemplate;
import com.examforge.trade.mapper.CouponTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 券模板管理（SUPER_ADMIN/OP，网关透传 X-User-Role；docs/14 C-2） */
@RestController
@RequestMapping("/api/v1/coupons/admin/templates")
@RequiredArgsConstructor
public class AdminCouponController {

    private final CouponTemplateMapper templateMapper;

    @GetMapping
    public Result<List<CouponTemplate>> list(@RequestHeader(value = "X-User-Role", required = false) String role) {
        requireRole(role);
        return Result.ok(templateMapper.selectList(new LambdaQueryWrapper<CouponTemplate>().orderByDesc(CouponTemplate::getId)));
    }

    @PostMapping
    public Result<Map<String, Object>> create(@RequestHeader(value = "X-User-Role", required = false) String role,
                                              @RequestBody CouponTemplate t) {
        requireRole(role);
        if (t.getName() == null || t.getType() == null) return Result.fail(Result.BAD_REQUEST, "名称与类型必填");
        if (t.getTotal() == null || t.getTotal() <= 0) t.setTotal(100);
        if (t.getPerLimit() == null) t.setPerLimit(1);
        if (t.getStatus() == null) t.setStatus(1);
        if (t.getGranted() == null) t.setGranted(0);
        templateMapper.insert(t);
        return Result.ok(Map.of("id", t.getId()));
    }

    @PutMapping("/{id}/status")
    public Result<Map<String, Object>> status(@RequestHeader(value = "X-User-Role", required = false) String role,
                                              @PathVariable Long id, @RequestBody Map<String, Object> body) {
        requireRole(role);
        CouponTemplate t = new CouponTemplate();
        t.setId(id);
        t.setStatus(Integer.parseInt(String.valueOf(body.get("status"))) == 1 ? 1 : 0);
        templateMapper.updateById(t);
        return Result.ok(Map.of("ok", true));
    }

    private void requireRole(String role) {
        if (!"SUPER_ADMIN".equals(role) && !"OP".equals(role)) {
            throw new com.examforge.common.web.GlobalExceptionHandler.BizException(Result.FORBIDDEN, "无权限");
        }
    }
}
