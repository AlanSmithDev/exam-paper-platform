package com.examforge.api.feign;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** UserStatsClient 契约结构锁（可脱离 Spring 单测）：batch 全路径须与 InternalUserController 的 /internal/users/batch 对齐 */
class UserStatsClientContractTest {

    @Test
    void batch端点为内部全路径() {
        assertEquals("/internal/users", UserStatsClient.class.getAnnotation(FeignClient.class).path());
        var m = Arrays.stream(UserStatsClient.class.getDeclaredMethods())
                .filter(x -> "batch".equals(x.getName())).findFirst().orElseThrow();
        assertArrayEquals(new String[]{"/batch"}, m.getAnnotation(GetMapping.class).value());
    }
}
