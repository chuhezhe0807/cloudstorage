package com.chuhezhe.notification.service;

import com.chuhezhe.notification.entity.Notification;
import com.chuhezhe.notification.mapper.NotificationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationMapper notificationMapper;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Test
    void createNotificationSuccess() {
        notificationService.createNotification(1L, 100L, "upload.completed",
                "{\"fileName\":\"test.txt\"}", "evt-1");

        verify(notificationMapper).insert(any(Notification.class));
    }

    @Test
    void createNotificationDuplicateIgnored() {
        when(notificationMapper.insert(any()))
                .thenThrow(new DuplicateKeyException("unique violation"));

        // 不应抛异常
        assertDoesNotThrow(() ->
                notificationService.createNotification(1L, 100L, "upload.completed",
                        "{}", "evt-1"));
    }

    @Test
    void markAsReadSuccess() {
        Notification notification = new Notification();
        notification.setId(1L);
        notification.setUserId(100L);

        when(notificationMapper.selectById(1L)).thenReturn(notification);

        notificationService.markAsRead(100L, 1L);

        verify(notificationMapper).updateById(argThat(n -> n.getReadAt() != null));
    }

    @Test
    void markAsReadWrongUserThrows() {
        Notification notification = new Notification();
        notification.setId(1L);
        notification.setUserId(100L);

        when(notificationMapper.selectById(1L)).thenReturn(notification);

        assertThrows(com.chuhezhe.common.exception.BusinessException.class,
                () -> notificationService.markAsRead(200L, 1L));
    }
}
