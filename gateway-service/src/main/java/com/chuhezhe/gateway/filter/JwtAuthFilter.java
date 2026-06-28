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

/**
 * 全局 JWT 鉴权过滤器。
 * 白名单路径放行；其他请求校验 Bearer Token，将 tenantId/userId 注入请求头。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter implements GlobalFilter, Ordered {

    // 无需鉴权的路径
    private static final List<String> WHITELIST_PATHS = List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh"
    );

    // 分享提取路径（访客无 JWT，需放行）
    private static final List<String> SHARE_ACCESS_PREFIX = List.of("/api/shares/");

    private final JwtUtil jwtUtil;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 白名单直接放行
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        // 提取并校验 Bearer Token
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return errorResponse(exchange, 401, "error.unauthorized");
        }

        try {
            String token = authHeader.substring(7);
            Claims claims = jwtUtil.validateToken(token);

            Long tenantId = claims.get("tenantId", Long.class);
            Long userId = claims.get("userId", Long.class);

            // 将 tenantId/userId 注入请求头，下游通过 TenantContextFilter 读取
            ServerHttpRequest request = exchange.getRequest().mutate()
                    .header("X-Tenant-Id", String.valueOf(tenantId))
                    .header("X-User-Id", String.valueOf(userId))
                    .build();

            return chain.filter(exchange.mutate().request(request).build());

        } catch (ExpiredJwtException e) {
            return errorResponse(exchange, 401, "error.token_expired");
        } catch (JwtException e) {
            log.warn("JWT 验证失败: {}", e.getMessage());
            return errorResponse(exchange, 401, "error.unauthorized");
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }

    // 检查路径是否在白名单
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

    // 统一错误响应（JSON）
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
