package com.examforge.practice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@MapperScan("com.examforge.practice.mapper")
@EnableFeignClients(basePackages = "com.examforge.api.feign")
public class PracticeApplication {
    public static void main(String[] args) {
        SpringApplication.run(PracticeApplication.class, args);
    }
}
