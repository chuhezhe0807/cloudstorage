package com.chuhezhe.user.service;

import com.chuhezhe.user.dto.UserPreferencesRequest;

public interface UserService {

    UserPreferencesRequest getPreferences(Long userId);

    void updatePreferences(Long userId, UserPreferencesRequest request);
}
