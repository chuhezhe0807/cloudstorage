package com.chuhezhe.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuhezhe.common.dto.PageResult;
import com.chuhezhe.common.exception.BusinessException;
import com.chuhezhe.common.result.ErrorCode;
import com.chuhezhe.notification.dto.NotificationVO;
import com.chuhezhe.notification.entity.Notification;
import com.chuhezhe.notification.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 站内信服务：创建（幂等）、查询、标记已读、删除。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;

    @Override
    @Transactional
    public void createNotification(Long tenantId, Long userId, String type, String payload, String eventId) {
        // 幂等：event_id 唯一约束保证
        Notification notification = new Notification();
        notification.setTenantId(tenantId);
        notification.setUserId(userId);
        notification.setType(type);
        notification.setPayload(payload);
        notification.setEventId(eventId);

        try {
            notificationMapper.insert(notification);
        } catch (DuplicateKeyException e) {
            log.debug("站内信已存在（幂等忽略）: eventId={}", eventId);
        }
    }

    @Override
    public PageResult<NotificationVO> listNotifications(Long userId, Boolean unreadOnly, int page, int size) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId);
        if (Boolean.TRUE.equals(unreadOnly)) {
            wrapper.isNull(Notification::getReadAt);
        }
        wrapper.orderByDesc(Notification::getCreatedAt);

        Page<Notification> p = new Page<>(page, size);
        p = notificationMapper.selectPage(p, wrapper);

        List<NotificationVO> vos = p.getRecords().stream()
                .map(n -> {
                    NotificationVO vo = new NotificationVO();
                    vo.setId(n.getId());
                    vo.setType(n.getType());
                    vo.setPayload(n.getPayload());
                    vo.setRead(n.getReadAt() != null);
                    vo.setCreatedAt(n.getCreatedAt());
                    return vo;
                })
                .collect(Collectors.toList());

        return new PageResult<>(vos, p.getTotal(), page, size);
    }

    @Override
    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationMapper.selectById(notificationId);
        if (notification == null || !userId.equals(notification.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        notification.setReadAt(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES));
        notificationMapper.updateById(notification);
    }

    @Override
    @Transactional
    public void deleteNotifications(Long userId, List<Long> ids) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Notification::getId, ids);
        wrapper.eq(Notification::getUserId, userId);
        notificationMapper.delete(wrapper);
    }
}
