package com.chuhezhe.notification.controller;

import com.chuhezhe.common.dto.PageResult;
import com.chuhezhe.common.result.Result;
import com.chuhezhe.notification.dto.NotificationVO;
import com.chuhezhe.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 站内信接口：查询、标记已读、删除。
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** 我的站内信列表 */
    @GetMapping
    public Result<PageResult<NotificationVO>> list(@RequestHeader("X-User-Id") Long userId,
                                                    @RequestParam(required = false) Boolean unread,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return Result.ok(notificationService.listNotifications(userId, unread, page, size));
    }

    /** 标记已读 */
    @PutMapping("/{id}/read")
    public Result<Void> markAsRead(@RequestHeader("X-User-Id") Long userId,
                                    @PathVariable Long id) {
        notificationService.markAsRead(userId, id);
        return Result.ok();
    }

    /** 批量删除 */
    @DeleteMapping
    public Result<Void> deleteBatch(@RequestHeader("X-User-Id") Long userId,
                                     @RequestParam List<Long> ids) {
        notificationService.deleteNotifications(userId, ids);
        return Result.ok();
    }
}
