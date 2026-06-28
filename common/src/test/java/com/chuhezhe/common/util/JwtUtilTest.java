package com.chuhezhe.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private final JwtUtil jwtUtil = new JwtUtil();

    @BeforeEach
    void setUp() {
        jwtUtil.setSecret("test-secret-key-that-is-at-least-256-bits-long-enough-for-hmac");
        jwtUtil.setAccessTokenTtl(60000);
        jwtUtil.setRefreshTokenTtl(300000);
    }

    @Test
    void generateAndValidateAccessToken() {
        String token = jwtUtil.generateAccessToken(1L, 100L);
        Claims claims = jwtUtil.validateToken(token);

        assertEquals(1L, jwtUtil.getTenantId(claims));
        assertEquals(100L, jwtUtil.getUserId(claims));
        assertEquals("zh", jwtUtil.getLocale(claims)); // 默认 zh
        assertFalse(jwtUtil.isTokenExpired(claims));
    }

    @Test
    void generateAccessTokenWithLocale() {
        String token = jwtUtil.generateAccessToken(1L, 100L, "en");
        Claims claims = jwtUtil.validateToken(token);

        assertEquals(1L, jwtUtil.getTenantId(claims));
        assertEquals(100L, jwtUtil.getUserId(claims));
        assertEquals("en", jwtUtil.getLocale(claims));
    }

    @Test
    void generateAndValidateRefreshToken() {
        String token = jwtUtil.generateRefreshToken(2L, 200L);
        Claims claims = jwtUtil.validateToken(token);

        assertEquals(2L, jwtUtil.getTenantId(claims));
        assertEquals(200L, jwtUtil.getUserId(claims));
    }

    @Test
    void expiredTokenThrows() {
        jwtUtil.setAccessTokenTtl(1);
        String token = jwtUtil.generateAccessToken(1L, 100L);

        try {
            Thread.sleep(10);
        } catch (InterruptedException ignored) {
        }

        Claims claims = jwtUtil.validateToken(token);
        assertTrue(jwtUtil.isTokenExpired(claims));
    }

    @Test
    void invalidTokenThrows() {
        assertThrows(JwtException.class, () -> jwtUtil.validateToken("invalid-token"));
    }

    @Test
    void shortSecretPadded() {
        jwtUtil.setSecret("short");
        String token = jwtUtil.generateAccessToken(1L, 100L);
        Claims claims = jwtUtil.validateToken(token);
        assertEquals(1L, jwtUtil.getTenantId(claims));
    }
}
