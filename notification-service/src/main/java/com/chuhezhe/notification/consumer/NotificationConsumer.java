package com.chuhezhe.notification.consumer;

import com.chuhezhe.notification.service.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * RabbitMQ 事件消费者：upload.completed / quota.exceeded / share.accessed。
 * 消费失败自动重试3次后入死信队列。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "cloudstorage.notifications")
    public void handleEvent(Map<String, Object> message) {
        String eventType = (String) message.get("eventType");
        String aggregateId = (String) message.get("aggregateId");
        String eventId = aggregateId + "-" + System.currentTimeMillis();

        log.info("收到事件: type={}, aggregateId={}", eventType, aggregateId);

        try {
            switch (eventType) {
                case "upload.completed" -> handleUploadCompleted(message, eventId);
                case "share.accessed" -> handleShareAccessed(message, eventId);
                case "quota.exceeded" -> handleQuotaExceeded(message, eventId);
                default -> log.warn("未知事件类型: {}", eventType);
            }
        } catch (Exception e) {
            log.error("事件处理失败: eventType={}", eventType, e);
            throw e; // 抛出触发重试
        }
    }

    private void handleUploadCompleted(Map<String, Object> message, String eventId) {
        String payload = (String) message.get("payload");
        try {
            JsonNode node = objectMapper.readTree(payload);
            Long tenantId = node.has("tenantId") ? node.get("tenantId").asLong() : 0L;
            Long ownerId = node.has("ownerId") ? node.get("ownerId").asLong() : 0L;
            String fileName = node.has("name") ? node.get("name").asText() : "未知文件";

            notificationService.createNotification(tenantId, ownerId, "upload.completed",
                    "{\"fileName\":\"" + fileName + "\"}", eventId);
        } catch (Exception ex) {
            log.error("解析上传完成事件失败", ex);
        }
    }

    private void handleShareAccessed(Map<String, Object> message, String eventId) {
        notificationService.createNotification(0L, 0L, "share.accessed",
                "{\"message\":\"分享被访问\"}", eventId);
    }

    private void handleQuotaExceeded(Map<String, Object> message, String eventId) {
        notificationService.createNotification(0L, 0L, "quota.exceeded",
                "{\"message\":\"配额超限\"}", eventId);
    }
}
