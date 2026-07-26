package com.chuhezhe.common.mq;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EventType {

    UPLOAD_COMPLETED("upload.completed", "notification.upload.completed"),
    SHARE_ACCESSED("share.accessed", "notification.share.accessed"),
    QUOTA_EXCEEDED("quota.exceeded", "notification.quota.exceeded");

    private final String typeName;
    private final String routingKey;

    public static EventType fromTypeName(String typeName) {
        for (EventType type : values()) {
            if (type.typeName.equals(typeName)) {
                return type;
            }
        }
        return null;
    }
}
