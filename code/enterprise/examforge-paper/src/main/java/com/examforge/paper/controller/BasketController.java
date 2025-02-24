package com.examforge.paper.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.examforge.common.web.GlobalExceptionHandler.BizException;
import com.examforge.common.web.Result;
import com.examforge.paper.domain.Basket;
import com.examforge.paper.mapper.BasketMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 试题篮（X-User-Id 由网关透传） */
@RestController
@RequestMapping("/api/v1/basket")
@RequiredArgsConstructor
public class BasketController {

    private static final int MAX_ITEMS = 100;
    private final BasketMapper basketMapper;

    private long uid(String header) {
        if (header == null) throw new BizException(Result.UNAUTHORIZED, "未登录");
        return Long.parseLong(header);
    }

    @GetMapping
    public Result<Map<String, Object>> get(@RequestHeader("X-User-Id") String uid) {
        List<Basket> items = basketMapper.selectList(new LambdaQueryWrapper<Basket>()
                .eq(Basket::getUserId, Long.valueOf(uid)).orderByDesc(Basket::getCreatedAt));
        return Result.ok(Map.of("count", items.size(), "ids", items.stream().map(Basket::getQuestionId).toList()));
    }

    @PostMapping("/items")
    public Result<Map<String, Object>> add(@RequestHeader("X-User-Id") String uid,
                                           @RequestBody Map<String, List<Long>> body) {
        List<Long> qids = body.getOrDefault("questionIds", List.of());
        long count = basketMapper.selectCount(new LambdaQueryWrapper<Basket>().eq(Basket::getUserId, Long.valueOf(uid)));
        int added = 0;
        for (Long qid : qids) {
            if (count + added >= MAX_ITEMS) throw new BizException(Result.BAD_REQUEST, "试题篮已达上限 " + MAX_ITEMS + " 题");
            Basket b = new Basket();
            b.setUserId(Long.valueOf(uid));
            b.setQuestionId(qid);
            added += basketMapper.insert(b) > 0 ? 1 : 0;
            if (added > 0 && basketMapper.selectCount(new LambdaQueryWrapper<Basket>()
                    .eq(Basket::getUserId, Long.valueOf(uid)).eq(Basket::getQuestionId, qid)) > 1) {
                added--; // 已存在则不计新增
            }
        }
        return Result.ok(Map.of("added", added));
    }

    @DeleteMapping("/items")
    public Result<Map<String, Object>> remove(@RequestHeader("X-User-Id") String uid,
                                              @RequestBody Map<String, List<Long>> body) {
        int removed = 0;
        for (Long qid : body.getOrDefault("questionIds", List.of())) {
            removed += basketMapper.delete(new LambdaQueryWrapper<Basket>()
                    .eq(Basket::getUserId, Long.valueOf(uid)).eq(Basket::getQuestionId, qid));
        }
        return Result.ok(Map.of("removed", removed));
    }
}
