package com.examforge.school.controller;

import com.examforge.school.service.SchoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 学校订阅内部端点（trade 权益联动：教师全员享会员权益的查询口径，docs/26 §7） */
@RestController
@RequestMapping("/internal/schools")
@RequiredArgsConstructor
public class InternalSchoolController {

    private final SchoolService schoolService;

    /** 用户学校身份 → {active, schoolId?, schoolName?, role?}（有效期+OPEN+角色判定在服务内） */
    @GetMapping("/membership")
    public Map<String, Object> membership(@org.springframework.web.bind.annotation.RequestParam("userId") Long userId) {
        return schoolService.membership(userId);
    }
}
