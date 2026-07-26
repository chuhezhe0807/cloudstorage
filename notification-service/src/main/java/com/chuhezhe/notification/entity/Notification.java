package com.chuhezhe.notification.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chuhezhe.common.entity.BaseEntity;
import com.chuhezhe.common.handler.JsonbTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "notification", autoResultMap = true)
public class Notification extends BaseEntity {

    private Long tenantId;
    private Long userId;
    private String type;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String payload;
    private String eventId;
    private LocalDateTime readAt;
}
