package com.examforge.api.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/** 学校服务 Feign 契约（T-26h 教师全员享权益：trade entitlement 学校维度查询，docs/26 §7） */
@FeignClient(name = "examforge-school", path = "/internal")
public interface SchoolClient {

    /** userId → {active, schoolId?, schoolName?, role?}（有效期+状态+角色判定在 school 服务内） */
    @GetMapping("/schools/membership")
    Map<String, Object> membership(@RequestParam("userId") Long userId);
}
