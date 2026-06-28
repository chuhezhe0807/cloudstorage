package com.chuhezhe.user.service;

import com.chuhezhe.user.dto.*;

public interface AuthService {

    LoginResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    LoginResponse refresh(RefreshRequest request);

    void logout(Long userId);
}
