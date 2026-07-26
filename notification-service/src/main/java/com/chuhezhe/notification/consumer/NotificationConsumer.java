package com.chuhezhe.notification.consumer;

import com.chuhezhe.common.mq.EventType;
import com.chuhezhe.common.mq.RabbitMqConstants;
import com.chuhezhe.notification.service.NotificationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMqConstants.NOTIFICATION_QUEUE)
    public void handleEvent(String body) {
        try {
            Map<String, Object> message = objectMapper.readValue(body, new TypeReference<>() {});
            String eventTypeStr = (String) message.get("eventType");
            String aggregateId = (String) message.get("aggregateId");
            String eventId = aggregateId + "-" + System.currentTimeMillis();

            EventType eventType = EventType.fromTypeName(eventTypeStr);
            log.info("收到事件: type={}, aggregateId={}", eventTypeStr, aggregateId);

            if (eventType == null) {
                log.warn("未知事件类型: {}", eventTypeStr);
                return;
            }

            switch (eventType) {
                case UPLOAD_COMPLETED -> handleUploadCompleted(message, eventId);
                case SHARE_ACCESSED -> handleShareAccessed(message, eventId);
                case QUOTA_EXCEEDED -> handleQuotaExceeded(eventId);
            }
        } catch (Exception e) {
            log.error("事件处理失败: body={}", body, e);
            throw new RuntimeException(e);
        }
    }

    private void handleUploadCompleted(Map<String, Object> message, String eventId) {
        String payload = (String) message.get("payload");
        try {
            JsonNode node = objectMapper.readTree(payload);
            Long tenantId = node.has("tenantId") ? node.get("tenantId").asLong() : 0L;
            Long ownerId = node.has("ownerId") ? node.get("ownerId").asLong() : 0L;
            String fileName = node.has("name") ? node.get("name").asText() : "未知文件";

            notificationService.createNotification(tenantId, ownerId, EventType.UPLOAD_COMPLETED.getTypeName(),
                    "{\"fileName\":\"" + fileName + "\"}", eventId);
        } catch (Exception ex) {
            log.error("解析上传完成事件失败", ex);
        }
    }

    private void handleShareAccessed(Map<String, Object> message, String eventId) {
        String payload = (String) message.get("payload");
        try {
            JsonNode node = objectMapper.readTree(payload);
            Long tenantId = node.has("tenantId") ? node.get("tenantId").asLong() : 0L;
            Long ownerId = node.has("ownerId") ? node.get("ownerId").asLong() : 0L;
            String fileName = node.has("fileName") ? node.get("fileName").asText() : "未知文件";

            notificationService.createNotification(tenantId, ownerId, EventType.SHARE_ACCESSED.getTypeName(),
                    "{\"fileName\":\"" + fileName + "\"}", eventId);
        } catch (Exception ex) {
            log.error("解析分享访问事件失败", ex);
        }
    }

    private void handleQuotaExceeded(String eventId) {
        notificationService.createNotification(0L, 0L, EventType.QUOTA_EXCEEDED.getTypeName(),
                "{\"message\":\"配额超限\"}", eventId);
    }
}
