package com.examforge.resource.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.examforge.common.web.Result;
import com.examforge.resource.domain.CopyrightAppeal;
import com.examforge.resource.domain.ResourceDownload;
import com.examforge.resource.domain.ResourceItem;
import com.examforge.resource.service.ResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 资源中心对外接口（网关路由 /api/v1/resources/**；列表/详情匿名可浏览，docs/26 §11） */
@RestController
@RequestMapping("/api/v1/resources")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;

    @GetMapping
    public Result<Page<ResourceItem>> page(@RequestParam(required = false) Integer stage,
                                           @RequestParam(required = false) String subject,
                                           @RequestParam(required = false) String category,
                                           @RequestParam(required = false) String level,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "20") long size) {
        return Result.ok(resourceService.page(stage, subject, category, level, keyword, page, size));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.ok(resourceService.detail(id));
    }

    // ---------- 资源篮 ----------

    @PostMapping("/{id}/basket")
    public Result<Map<String, Object>> addBasket(@RequestHeader("X-User-Id") String uid, @PathVariable Long id) {
        return Result.ok(resourceService.addToBasket(Long.valueOf(uid), id));
    }

    @DeleteMapping("/{id}/basket")
    public Result<Map<String, Object>> removeBasket(@RequestHeader("X-User-Id") String uid, @PathVariable Long id) {
        return Result.ok(resourceService.removeFromBasket(Long.valueOf(uid), id));
    }

    @GetMapping("/basket")
    public Result<List<ResourceItem>> basket(@RequestHeader("X-User-Id") String uid) {
        return Result.ok(resourceService.basket(Long.valueOf(uid)));
    }

    /** 资源篮批量结算：判价汇总一次扣点，逐件落账并清空资源篮（docs/26 F-XKW-03） */
    @PostMapping("/basket/checkout")
    public Result<Map<String, Object>> checkout(@RequestHeader("X-User-Id") String uid) {
        return Result.ok(resourceService.checkoutBasket(Long.valueOf(uid)));
    }

    // ---------- 计费下载 ----------

    @PostMapping("/{id}/download")
    public Result<Map<String, Object>> download(@RequestHeader("X-User-Id") String uid, @PathVariable Long id) {
        return Result.ok(resourceService.download(Long.valueOf(uid), id));
    }

    @GetMapping("/downloads/mine")
    public Result<List<ResourceDownload>> myDownloads(@RequestHeader("X-User-Id") String uid) {
        return Result.ok(resourceService.myDownloads(Long.valueOf(uid)));
    }

    // ---------- 版权异议/申诉 ----------

    @PostMapping("/appeals")
    public Result<Map<String, Object>> submitAppeal(@RequestHeader("X-User-Id") String uid,
                                                    @RequestBody Map<String, Object> body) {
        return Result.ok(resourceService.submitAppeal(Long.valueOf(uid),
                String.valueOf(body.getOrDefault("targetType", "")),
                body.get("targetId") == null ? null : Long.valueOf(String.valueOf(body.get("targetId"))),
                String.valueOf(body.getOrDefault("appealType", "OTHER")),
                String.valueOf(body.getOrDefault("content", "")),
                String.valueOf(body.getOrDefault("contact", ""))));
    }

    @PostMapping("/appeals/{id}/withdraw")
    public Result<Map<String, Object>> withdraw(@RequestHeader("X-User-Id") String uid, @PathVariable Long id) {
        return Result.ok(resourceService.withdrawAppeal(Long.valueOf(uid), id));
    }

    @GetMapping("/appeals/mine")
    public Result<List<CopyrightAppeal>> myAppeals(@RequestHeader("X-User-Id") String uid) {
        return Result.ok(resourceService.myAppeals(Long.valueOf(uid)));
    }
}
