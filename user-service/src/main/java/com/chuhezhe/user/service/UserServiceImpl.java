package com.chuhezhe.user.service;

import com.chuhezhe.common.exception.BusinessException;
import com.chuhezhe.common.result.ErrorCode;
import com.chuhezhe.user.dto.UserPreferencesRequest;
import com.chuhezhe.user.entity.User;
import com.chuhezhe.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 用户服务：偏好（语言/主题）读写。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Override
    public UserPreferencesRequest getPreferences(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        UserPreferencesRequest prefs = new UserPreferencesRequest();
        prefs.setLocale(user.getLocale());
        prefs.setTheme(user.getTheme());
        return prefs;
    }

    @Override
    public void updatePreferences(Long userId, UserPreferencesRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        // 仅更新传入的非空字段
        Optional.ofNullable(request.getLocale()).ifPresent(user::setLocale);
        Optional.ofNullable(request.getTheme()).ifPresent(user::setTheme);
        userMapper.updateById(user);
        log.info("用户偏好已更新: userId={}", userId);
    }
}
