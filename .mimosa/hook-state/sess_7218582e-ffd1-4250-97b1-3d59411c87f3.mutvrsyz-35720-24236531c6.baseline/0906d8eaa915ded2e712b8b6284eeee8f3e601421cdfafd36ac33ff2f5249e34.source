package com.examforge.school;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/** 学校订阅服务（T-26h，docs/26 §7 网校通：学校开通 → 教师全员享权益） */
@SpringBootApplication
@MapperScan("com.examforge.school.mapper")
@EnableFeignClients(basePackages = "com.examforge.api.feign")
public class SchoolApplication {
    public static void main(String[] args) {
        SpringApplication.run(SchoolApplication.class, args);
    }
}
