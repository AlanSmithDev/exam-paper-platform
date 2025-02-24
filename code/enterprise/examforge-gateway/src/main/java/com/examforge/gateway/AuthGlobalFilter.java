package com.examforge.gateway;

import com.examforge.common.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 网关全局鉴权过滤器：
 * 1) 白名单直接放行；2) 其余 /api/** 必须携带有效 JWT；
 * 3) 校验通过后向下游服务透传 X-User-Id / X-User-Role（服务内不再解析 token）。
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;
    private final List<String> whitelist;

    public AuthGlobalFilter(@Value("${examforge.jwt.secret}") String secret,
                            @Value("${examforge.jwt.ttl-hours:12}") long ttlHours,
                            @Value("${examforge.whitelist}") List<String> whitelist) {
        this.jwtUtil = new JwtUtil(secret, ttlHours * 3600_000L);
        this.whitelist = whitelist;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        // 安全：服务间内部接口只允许内网/服务间调用，绝不经网关外泄
        if (path.contains("/internal/")) {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }
        if (whitelist.stream().anyMatch(path::startsWith) || resourcePublicRead(path)) {
            // 匿名放行：剥离客户端自带身份头（身份只由网关校验 JWT 后注入，防伪造）
            ServerWebExchange clean = exchange.mutate()
                    .request(r -> r.headers(h -> {
                        h.remove("X-User-Id");
                        h.remove("X-User-Role");
                        h.remove("X-User-Nickname");
                    })).build();
            return chain.filter(clean);
        }
        String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        JwtUtil.AuthUser user = jwtUtil.verify(auth.substring(7));
        if (user == null) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        ServerWebExchange mutated = exchange.mutate()
                .request(r -> r.headers(h -> {
                    h.set("X-User-Id", String.valueOf(user.uid()));
                    h.set("X-User-Role", user.role() == null ? "" : user.role());
                    h.set("X-User-Nickname", user.nickname() == null ? "" : user.nickname());
                }))
                .build();
        return chain.filter(mutated);
    }

    @Override
    public int getOrder() { return -100; }

    /** 资源中心公开读：列表/数字详情匿名 + 创作者月收入榜公开榜（docs/26 §6）；资源篮/下载/创作者其余/申诉等须登录 */
    static boolean resourcePublicRead(String path) {
        return path.equals("/api/v1/resources") || path.matches("/api/v1/resources/\\d+")
                || path.equals("/api/v1/resources/creator/board");
    }
}
