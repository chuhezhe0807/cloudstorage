package com.chuhezhe.user.service;

import com.chuhezhe.common.result.ErrorCode;
import com.chuhezhe.common.util.JwtUtil;
import com.chuhezhe.user.dto.*;
import com.chuhezhe.user.entity.Tenant;
import com.chuhezhe.user.entity.User;
import com.chuhezhe.user.mapper.TenantMapper;
import com.chuhezhe.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private TenantMapper tenantMapper;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_password");
    }

    @Test
    void registerSuccess() {
        RegisterRequest req = new RegisterRequest();
        req.setAccount("testuser");
        req.setPassword("password123");

        when(userMapper.findByAccount("testuser")).thenReturn(Optional.empty());
        when(jwtUtil.generateAccessToken(anyLong(), anyLong(), anyString())).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(anyLong(), anyLong(), anyString())).thenReturn("refresh-token");

        LoginResponse response = authService.register(req);

        assertNotNull(response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        verify(tenantMapper).insert(any(Tenant.class));
        verify(userMapper).insert(any(User.class));
    }

    @Test
    void registerDuplicateAccountThrows() {
        RegisterRequest req = new RegisterRequest();
        req.setAccount("existing");
        req.setPassword("password123");

        when(userMapper.findByAccount("existing")).thenReturn(Optional.of(new User()));

        com.chuhezhe.common.exception.BusinessException ex = assertThrows(
                com.chuhezhe.common.exception.BusinessException.class, () -> authService.register(req));
        assertEquals(ErrorCode.ACCOUNT_EXISTS.getCode(), ex.getCode());
    }

    @Test
    void loginSuccess() {
        LoginRequest req = new LoginRequest();
        req.setAccount("testuser");
        req.setPassword("password123");

        User user = new User();
        user.setId(100L);
        user.setTenantId(1L);
        user.setAccount("testuser");
        user.setPasswordHash("hashed_password");

        when(userMapper.findByAccount("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed_password")).thenReturn(true);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(jwtUtil.generateAccessToken(anyLong(), anyLong(), anyString())).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(anyLong(), anyLong(), anyString())).thenReturn("refresh-token");

        LoginResponse response = authService.login(req);

        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
    }

    @Test
    void loginWrongPasswordThrows() {
        LoginRequest req = new LoginRequest();
        req.setAccount("testuser");
        req.setPassword("wrong");

        User user = new User();
        user.setPasswordHash("hashed_password");

        when(userMapper.findByAccount("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed_password")).thenReturn(false);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), any())).thenReturn(true);

        com.chuhezhe.common.exception.BusinessException ex = assertThrows(
                com.chuhezhe.common.exception.BusinessException.class, () -> authService.login(req));
        assertEquals(ErrorCode.INVALID_CREDENTIALS.getCode(), ex.getCode());
    }
}
