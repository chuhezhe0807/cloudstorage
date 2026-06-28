package com.chuhezhe.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

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

    public String generateAccessToken(Long tenantId, Long userId) {
        return generateToken(tenantId, userId, accessTokenTtl);
    }

    public String generateRefreshToken(Long tenantId, Long userId) {
        return generateToken(tenantId, userId, refreshTokenTtl);
    }

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

    public boolean isTokenExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }

    private String generateToken(Long tenantId, Long userId, long ttl) {
        Date now = new Date();
        return Jwts.builder()
                .claim("tenantId", tenantId)
                .claim("userId", userId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttl))
                .signWith(getKey())
                .compact();
    }

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
