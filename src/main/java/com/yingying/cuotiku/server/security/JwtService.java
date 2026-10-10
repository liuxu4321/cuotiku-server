package com.yingying.cuotiku.server.security;

import com.yingying.cuotiku.server.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtService {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";
    public static final long ROTATION_GRACE_SECONDS = 120;

    private final SecretKey key;
    private final long ttlSeconds;
    private final long refreshTtlSeconds;

    public JwtService(AppProperties properties) {
        String configured = properties.jwt().secret();
        if (configured == null || configured.isBlank() || configured.startsWith("${")) {
            throw new IllegalStateException("app.jwt.secret 未配置，请设置环境变量 JWT_SECRET");
        }
        byte[] secret = configured.getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("app.jwt.secret 长度不足，至少需要32字节");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.ttlSeconds = properties.jwt().ttlHours() * 3600;
        this.refreshTtlSeconds = properties.jwt().refreshTtlDays() * 86400;
    }

    public record IssuedToken(String token, String jti, Instant expiresAt) {}

    public IssuedToken issueAccess(String phone, String role) {
        return issue(phone, TYPE_ACCESS, ttlSeconds, builder -> builder.claim("role", role));
    }

    /** 身份令牌与账号访问令牌使用不同typ，不能访问业务接口。 */
    public IssuedToken issueMiniResource(String id, String purpose) {
        return issue(id, purpose, 300, builder -> builder);
    }

    public IssuedToken issueMiniIdentity(String identityId) {
        return issue(identityId, "mini_identity", 300, builder -> builder);
    }

    public IssuedToken issueRefresh(String phone) {
        return issue(phone, TYPE_REFRESH, refreshTtlSeconds, builder -> builder);
    }

    public IssuedToken issueAccessWithJti(String phone, String role, String jti) {
        return issueWithJti(phone, TYPE_ACCESS, ttlSeconds, jti,
                builder -> builder.claim("role", role));
    }

    public IssuedToken issueRefreshWithJti(String phone, String jti) {
        return issueWithJti(phone, TYPE_REFRESH, refreshTtlSeconds, jti, builder -> builder);
    }

    private IssuedToken issueWithJti(String phone, String type, long ttl, String jti,
                                     java.util.function.UnaryOperator<io.jsonwebtoken.JwtBuilder> customizer) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(ttl);
        io.jsonwebtoken.JwtBuilder builder = Jwts.builder()
                .id(jti)
                .subject(phone)
                .claim("typ", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt));
        String token = customizer.apply(builder).signWith(key).compact();
        return new IssuedToken(token, jti, expiresAt);
    }

    private IssuedToken issue(String phone, String type, long ttl,
                              java.util.function.UnaryOperator<io.jsonwebtoken.JwtBuilder> customizer) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(ttl);
        String jti = UUID.randomUUID().toString().replace("-", "");
        io.jsonwebtoken.JwtBuilder builder = Jwts.builder()
                .id(jti)
                .subject(phone)
                .claim("typ", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt));
        String token = customizer.apply(builder).signWith(key).compact();
        return new IssuedToken(token, jti, expiresAt);
    }

    public Claims parse(String token) throws JwtException {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }

    public long refreshTtlSeconds() {
        return refreshTtlSeconds;
    }
}
