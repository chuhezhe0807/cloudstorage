package com.chuhezhe.user.service;

import com.chuhezhe.common.exception.BusinessException;
import com.chuhezhe.common.result.ErrorCode;
import com.chuhezhe.common.util.JwtUtil;
import com.chuhezhe.user.dto.*;
import com.chuhezhe.user.entity.Tenant;
import com.chuhezhe.user.entity.User;
import com.chuhezhe.user.mapper.TenantMapper;
import com.chuhezhe.user.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;

/**
 * 认证服务实现：注册、登录、刷新令牌、登出。
 * <p>
 * 安全机制：
 * - 密码 BCrypt 哈希存储
 * - 登录 5 次失败锁定 15 分钟（Redis 计数）
 * - Refresh Token 一次性旋转，重复使用则撤销全部令牌
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    // Redis Key 模板
    private static final String LOGIN_FAIL_COUNT_KEY = "login_fail:%s";
    private static final String LOGIN_LOCK_KEY = "login_lock:%s";
    private static final String REFRESH_TOKEN_KEY = "rt:%d";
    private static final String REFRESH_TOKEN_USED_KEY = "rt_used:%s";

    // Token 有效期
    private static final long ACCESS_TTL = 900_000;       // 15分钟
    private static final long REFRESH_TTL = 604_800_000;  // 7天

    private final UserMapper userMapper;
    private final TenantMapper tenantMapper;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    @Override
    @Transactional
    public LoginResponse register(RegisterRequest request) {
        // 账号唯一性校验
        if (userMapper.findByAccount(request.getAccount()).isPresent()) {
            throw new BusinessException(ErrorCode.ACCOUNT_EXISTS);
        }

        // 创建个人租户，默认配额 1GB
        Tenant tenant = new Tenant();
        tenant.setName(request.getAccount());
        tenant.setType("personal");
        tenant.setQuota(1073741824L);
        tenant.setUsed(0L);
        tenantMapper.insert(tenant);

        // 创建用户（owner 角色）
        User user = new User();
        user.setTenantId(tenant.getId());
        user.setAccount(request.getAccount());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole("owner");
        user.setLocale(Optional.ofNullable(request.getLocale()).orElse("zh"));
        user.setTheme(Optional.ofNullable(request.getTheme()).orElse("light"));
        userMapper.insert(user);

        // 签发双 Token
        String accessToken = jwtUtil.generateAccessToken(tenant.getId(), user.getId());
        String refreshToken = jwtUtil.generateRefreshToken(tenant.getId(), user.getId());

        storeRefreshToken(user.getId(), refreshToken);

        log.info("用户注册成功: account={}, tenantId={}", user.getAccount(), tenant.getId());
        return new LoginResponse(accessToken, refreshToken, ACCESS_TTL / 1000);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        // 检查账号是否被锁定
        String lockKey = String.format(LOGIN_LOCK_KEY, request.getAccount());
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        // 查用户，不存在则记录失败
        User user = userMapper.findByAccount(request.getAccount())
                .orElseThrow(() -> {
                    recordLoginFailure(request.getAccount());
                    return new BusinessException(ErrorCode.INVALID_CREDENTIALS);
                });

        // 验密，失败记录
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            recordLoginFailure(request.getAccount());
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 登录成功，清除失败计数
        clearLoginFailures(request.getAccount());

        // 签发双 Token
        String accessToken = jwtUtil.generateAccessToken(user.getTenantId(), user.getId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getTenantId(), user.getId());

        storeRefreshToken(user.getId(), refreshToken);

        log.info("用户登录成功: account={}, tenantId={}", user.getAccount(), user.getTenantId());
        return new LoginResponse(accessToken, refreshToken, ACCESS_TTL / 1000);
    }

    @Override
    public LoginResponse refresh(RefreshRequest request) {
        String oldToken = request.getRefreshToken();

        // 解析 Refresh Token（JWT格式，可提取 userId）
        Claims claims;
        try {
            claims = jwtUtil.validateToken(oldToken);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        Long userId = jwtUtil.getUserId(claims);
        Long tenantId = jwtUtil.getTenantId(claims);

        // 比对 Redis 中存储的活跃 Token
        String tokenKey = String.format(REFRESH_TOKEN_KEY, userId);
        String storedToken = redisTemplate.opsForValue().get(tokenKey);

        if (storedToken == null || !storedToken.equals(oldToken)) {
            // Token 不存在或已被旋转 —— 疑似盗用，撤销全部令牌
            if (storedToken == null) {
                markTokenUsed(oldToken);
                revokeAllUserTokens(userId);
            }
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 验证用户仍存在
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 旋转：签发新 Token，标记旧 Token 已用
        String accessToken = jwtUtil.generateAccessToken(tenantId, userId);
        String newRefreshToken = jwtUtil.generateRefreshToken(tenantId, userId);

        markTokenUsed(oldToken);
        storeRefreshToken(userId, newRefreshToken);

        log.info("令牌刷新成功: userId={}", userId);
        return new LoginResponse(accessToken, newRefreshToken, ACCESS_TTL / 1000);
    }

    @Override
    public void logout(Long userId) {
        revokeAllUserTokens(userId);
        log.info("用户登出: userId={}", userId);
    }

    // 存储活跃 Refresh Token 到 Redis
    private void storeRefreshToken(Long userId, String token) {
        redisTemplate.opsForValue().set(
                String.format(REFRESH_TOKEN_KEY, userId), token,
                Duration.ofMillis(REFRESH_TTL));
    }

    // 标记 Token 已被使用（防止重复刷新）
    private void markTokenUsed(String token) {
        redisTemplate.opsForValue().set(
                String.format(REFRESH_TOKEN_USED_KEY, token), "1",
                Duration.ofMillis(REFRESH_TTL));
    }

    // 撤销某用户的所有 Refresh Token
    private void revokeAllUserTokens(Long userId) {
        redisTemplate.delete(String.format(REFRESH_TOKEN_KEY, userId));
    }

    // 记录登录失败，5次后锁定15分钟
    private void recordLoginFailure(String account) {
        String failKey = String.format(LOGIN_FAIL_COUNT_KEY, account);
        Long count = redisTemplate.opsForValue().increment(failKey);
        redisTemplate.expire(failKey, Duration.ofMinutes(5));

        if (count != null && count >= 5) {
            redisTemplate.opsForValue().set(
                    String.format(LOGIN_LOCK_KEY, account), "1",
                    Duration.ofMinutes(15));
            log.warn("账号已锁定: account={}", account);
        }
    }

    // 登录成功后清除失败计数和锁定
    private void clearLoginFailures(String account) {
        redisTemplate.delete(String.format(LOGIN_FAIL_COUNT_KEY, account));
        redisTemplate.delete(String.format(LOGIN_LOCK_KEY, account));
    }
}
