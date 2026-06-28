package com.chuhezhe.gateway.filter;

import com.chuhezhe.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final List<String> WHITELIST_PATHS = List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh"
    );

    private static final List<String> SHARE_ACCESS_PREFIX = List.of("/api/shares/");

    private final JwtUtil jwtUtil;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return errorResponse(exchange, 401, "error.unauthorized");
        }

        try {
            String token = authHeader.substring(7);
            Claims claims = jwtUtil.validateToken(token);

            Long tenantId = claims.get("tenantId", Long.class);
            Long userId = claims.get("userId", Long.class);

            ServerHttpRequest request = exchange.getRequest().mutate()
                    .header("X-Tenant-Id", String.valueOf(tenantId))
                    .header("X-User-Id", String.valueOf(userId))
                    .build();

            return chain.filter(exchange.mutate().request(request).build());

        } catch (ExpiredJwtException e) {
            return errorResponse(exchange, 401, "error.token_expired");
        } catch (JwtException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return errorResponse(exchange, 401, "error.unauthorized");
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean isWhitelisted(String path) {
        if (WHITELIST_PATHS.contains(path)) {
            return true;
        }
        for (String prefix : SHARE_ACCESS_PREFIX) {
            if (path.startsWith(prefix) && path.endsWith("/access")) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> errorResponse(ServerWebExchange exchange, int code, String i18nKey) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.valueOf(code));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format("{\"code\":%d,\"message\":\"%s\"}", code, i18nKey);
        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
