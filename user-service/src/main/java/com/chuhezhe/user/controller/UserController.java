package com.chuhezhe.user.controller;

import com.chuhezhe.common.result.Result;
import com.chuhezhe.user.dto.UserPreferencesRequest;
import com.chuhezhe.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户偏好接口：语言、主题读写。
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 获取当前用户的语言/主题偏好 */
    @GetMapping("/preferences")
    public Result<UserPreferencesRequest> getPreferences(@RequestHeader("X-User-Id") Long userId) {
        return Result.ok(userService.getPreferences(userId));
    }

    /** 更新语言/主题偏好 */
    @PutMapping("/preferences")
    public Result<Void> updatePreferences(@RequestHeader("X-User-Id") Long userId,
                                           @Valid @RequestBody UserPreferencesRequest request) {
        userService.updatePreferences(userId, request);
        return Result.ok();
    }
}
