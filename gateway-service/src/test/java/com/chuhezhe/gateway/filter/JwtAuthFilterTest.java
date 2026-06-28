package com.chuhezhe.gateway.filter;

import com.chuhezhe.common.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;

class JwtAuthFilterTest {

    private final JwtUtil jwtUtil = new JwtUtil();
    private final JwtAuthFilter filter = new JwtAuthFilter(jwtUtil);

    @BeforeEach
    void setUp() {
        jwtUtil.setSecret("test-secret-key-that-is-at-least-256-bits-long-enough-for-hmac");
        jwtUtil.setAccessTokenTtl(60000);
    }

    @Test
    void whitelistLoginPasses() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login").build());
        GatewayFilterChain chain = (e) -> Mono.empty();

        Mono<Void> result = filter.filter(exchange, chain);
        assertNotNull(result);
    }

    @Test
    void whitelistRegisterPasses() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/register").build());
        GatewayFilterChain chain = (e) -> Mono.empty();

        Mono<Void> result = filter.filter(exchange, chain);
        assertNotNull(result);
    }

    @Test
    void whitelistRefreshPasses() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/refresh").build());
        GatewayFilterChain chain = (e) -> Mono.empty();

        Mono<Void> result = filter.filter(exchange, chain);
        assertNotNull(result);
    }

    @Test
    void shareAccessPasses() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/shares/abc123/access").build());
        GatewayFilterChain chain = (e) -> Mono.empty();

        Mono<Void> result = filter.filter(exchange, chain);
        assertNotNull(result);
    }

    @Test
    void missingTokenReturns401() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/files").build());
        GatewayFilterChain chain = (e) -> {
            fail("chain should not be called");
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();
        assertTrue(exchange.getResponse().getStatusCode().value() == 401);
    }

    @Test
    void validTokenInjectsHeaders() {
        String token = jwtUtil.generateAccessToken(1L, 100L);
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/files")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build());

        var headers = new Object() {
            String tenantId;
            String userId;
        };
        GatewayFilterChain chain = (e) -> {
            headers.tenantId = e.getRequest().getHeaders().getFirst("X-Tenant-Id");
            headers.userId = e.getRequest().getHeaders().getFirst("X-User-Id");
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();
        assertEquals("1", headers.tenantId);
        assertEquals("100", headers.userId);
    }

    @Test
    void invalidTokenReturns401() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/files")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                        .build());
        GatewayFilterChain chain = (e) -> {
            fail("chain should not be called");
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();
        assertTrue(exchange.getResponse().getStatusCode().value() == 401);
    }
}
