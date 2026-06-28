package com.chuhezhe.notification.service;

import com.chuhezhe.common.dto.PageResult;
import com.chuhezhe.notification.dto.NotificationVO;

public interface NotificationService {

    /** 创建站内信（幂等：按 eventId 去重） */
    void createNotification(Long tenantId, Long userId, String type, String payload, String eventId);

    /** 分页查询站内信 */
    PageResult<NotificationVO> listNotifications(Long userId, Boolean unreadOnly, int page, int size);

    /** 标记已读 */
    void markAsRead(Long userId, Long notificationId);

    /** 批量删除 */
    void deleteNotifications(Long userId, java.util.List<Long> ids);
}
