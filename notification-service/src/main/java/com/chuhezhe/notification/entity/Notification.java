package com.chuhezhe.notification.entity;

import com.chuhezhe.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 站内信实体：事件类型、payload、已读状态。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notification")
public class Notification extends BaseEntity {

    private Long tenantId;
    private Long userId;
    private String type;
    private String payload;
    private String eventId;
    private LocalDateTime readAt;
}
