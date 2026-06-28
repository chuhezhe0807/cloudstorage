package com.chuhezhe.notification.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotificationVO {

    private Long id;
    private String type;
    private String payload;
    private boolean read;
    private LocalDateTime createdAt;
}
