package com.chuhezhe.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类：签发/校验 Access Token 和 Refresh Token。
 * 配置前缀 jwt.secret / jwt.access-token-ttl / jwt.refresh-token-ttl。
 */
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtUtil {

    private String secret;
    private long accessTokenTtl = 900_000;
    private long refreshTokenTtl = 604_800_000;

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public void setAccessTokenTtl(long accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public void setRefreshTokenTtl(long refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }

    /** 签发 Access Token（短期，默认15分钟） */
    public String generateAccessToken(Long tenantId, Long userId) {
        return generateToken(tenantId, userId, "zh", accessTokenTtl);
    }

    /** 签发 Access Token（带语言偏好） */
    public String generateAccessToken(Long tenantId, Long userId, String locale) {
        return generateToken(tenantId, userId, locale, accessTokenTtl);
    }

    /** 签发 Refresh Token（长期，默认7天） */
    public String generateRefreshToken(Long tenantId, Long userId) {
        return generateToken(tenantId, userId, "zh", refreshTokenTtl);
    }

    /** 签发 Refresh Token（带语言偏好） */
    public String generateRefreshToken(Long tenantId, Long userId, String locale) {
        return generateToken(tenantId, userId, locale, refreshTokenTtl);
    }

    /** 校验 Token 签名并返回 Claims */
    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getTenantId(Claims claims) {
        return claims.get("tenantId", Long.class);
    }

    public Long getUserId(Claims claims) {
        return claims.get("userId", Long.class);
    }

    public String getLocale(Claims claims) {
        String locale = claims.get("locale", String.class);
        return locale != null ? locale : "zh";
    }

    public boolean isTokenExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }

    /** HS256 签名生成 JWT */
    private String generateToken(Long tenantId, Long userId, String locale, long ttl) {
        Date now = new Date();
        return Jwts.builder()
                .claim("tenantId", tenantId)
                .claim("userId", userId)
                .claim("locale", locale)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttl))
                .signWith(getKey())
                .compact();
    }

    /** 获取签名密钥，不足32字节时自动补0到256位 */
    private SecretKey getKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
            return Keys.hmacShaKeyFor(padded);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
