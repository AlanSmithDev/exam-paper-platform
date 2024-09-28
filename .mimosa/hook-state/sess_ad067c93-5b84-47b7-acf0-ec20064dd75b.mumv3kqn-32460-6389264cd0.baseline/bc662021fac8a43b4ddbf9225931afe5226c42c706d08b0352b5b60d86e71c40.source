package com.examforge.api.feign;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** TradeClient 契约结构锁（可脱离 Spring 单测）：全路径与服务端控制器一致，防类级 path 前缀化 404 回归 */
class TradeClientContractTest {

    @Test
    void feign全路径_公开端点带v1_内部端点为internal() {
        List<String> paths = fullPaths();
        assertTrue(paths.contains("/api/v1/trade/billing"));
        assertTrue(paths.contains("/api/v1/trade/consume"));
        assertTrue(paths.contains("/api/v1/member/me"));
        assertTrue(paths.contains("/internal/trade/points/reward"));
        assertTrue(paths.contains("/internal/trade/points/deduct"));
        assertTrue(paths.contains("/internal/trade/points/credit"));
        // 内部端点绝不允许落到 /api/v1 前缀下（须与 InternalTradeController 的 /internal/trade/** 对齐）
        paths.stream().filter(p -> p.contains("/internal/"))
                .forEach(p -> assertFalse(p.startsWith("/api/v1"), "内部端点混入 /api/v1 前缀: " + p));
    }

    private static List<String> fullPaths() {
        List<String> out = new ArrayList<>();
        for (var m : TradeClient.class.getDeclaredMethods()) {
            GetMapping get = m.getAnnotation(GetMapping.class);
            if (get != null) out.addAll(List.of(get.value()));
            PostMapping post = m.getAnnotation(PostMapping.class);
            if (post != null) out.addAll(List.of(post.value()));
        }
        assertFalse(out.isEmpty(), "TradeClient 未解析到任何路径");
        return out;
    }
}
