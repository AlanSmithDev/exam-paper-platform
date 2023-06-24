package com.examforge.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import javax.crypto.SecretKey;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/** JWT 签发/校验（HS256）。密钥从环境变量注入，禁止硬编码到代码。 */
public class JwtUtil {

    private final SecretKey key;
    private final long ttlMillis;

    public JwtUtil(String secret, long ttlMillis) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMillis = ttlMillis;
    }

    public String sign(Long uid, String role, String nickname) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(uid))
                .claim("role", role)
                .claim("nickname", nickname)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis))
                .signWith(key)
                .compact();
    }

    /** 校验并返回载荷；无效返回 null */
    public AuthUser verify(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            return new AuthUser(Long.valueOf(c.getSubject()), c.get("role", String.class), c.get("nickname", String.class));
        } catch (Exception e) {
            return null;
        }
    }

    public record AuthUser(Long uid, String role, String nickname) { }
}
