package com.examforge.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * JwtUtil 自动装配（各服务经 examforge-common 传递获得；容器化部署联调期补齐——
 * 原各服务手动 new（gateway）/无 Bean 来源（user）混用）。
 */
@AutoConfiguration
public class JwtAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtUtil jwtUtil(@Value("${JWT_SECRET:examforge-dev-secret-change-in-prod-0123456789abcdef}") String secret,
                           @Value("${JWT_TTL_HOURS:12}") long ttlHours) {
        return new JwtUtil(secret, ttlHours * 3600_000L);
    }
}
