package com.examforge.resource.controller;

import com.examforge.common.web.Result;
import com.examforge.resource.domain.ResourceItem;
import com.examforge.resource.service.ResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 创作者中心（docs/26 §6：上传→待审→上架→下载计费→分成入账；月度结算提现为 P3） */
@RestController
@RequestMapping("/api/v1/resources/creator")
@RequiredArgsConstructor
public class CreatorResourceController {

    private final ResourceService resourceService;

    /** 上传资源：creator_user_id=当前用户，status=0 进既有 admin 上架审核流 */
    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestHeader("X-User-Id") String uid,
                                              @RequestBody ResourceItem body) {
        return Result.ok(resourceService.upload(Long.valueOf(uid), body));
    }

    /** 收益查询：分成流水（分页）+ 累计分成 */
    @GetMapping("/earnings")
    public Result<Map<String, Object>> earnings(@RequestHeader("X-User-Id") String uid,
                                                @RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "20") long size) {
        return Result.ok(resourceService.earnings(Long.valueOf(uid), page, size));
    }

    /** 月收入榜（公开榜，docs/26 §6 强激励展示；网关对 board 匿名放行） */
    @GetMapping("/board")
    public Result<List<Map<String, Object>>> board(@RequestParam(required = false) String month,
                                                   @RequestParam(required = false) Integer limit) {
        return Result.ok(resourceService.monthBoard(month, limit));
    }
}
