package com.chuhezhe.core.storage.service;

import com.chuhezhe.common.mq.EventType;
import com.chuhezhe.common.mq.RabbitMqConstants;
import com.chuhezhe.core.storage.entity.OutboxEvent;
import com.chuhezhe.core.storage.mapper.OutboxEventMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRIES = 3;

    private final OutboxEventMapper outboxEventMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 3000)
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxEventMapper.selectPendingEvents(BATCH_SIZE);
        if (events.isEmpty()) {
            return;
        }

        log.debug("发现 {} 条待发送事件", events.size());
        for (OutboxEvent event : events) {
            publishSingleEvent(event);
        }
    }

    private void publishSingleEvent(OutboxEvent event) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("eventType", event.getEventType());
            message.put("aggregateId", event.getAggregateId());
            message.put("payload", event.getPayload());

            String json = objectMapper.writeValueAsString(message);

            EventType eventType = EventType.fromTypeName(event.getEventType());
            String routingKey = eventType != null ? eventType.getRoutingKey() : "notification." + event.getEventType();
            rabbitTemplate.convertAndSend(RabbitMqConstants.EXCHANGE, routingKey, json);

            outboxEventMapper.markSent(event.getId());
            log.info("outbox 事件发送成功: id={}, eventType={}", event.getId(), event.getEventType());
        } catch (Exception e) {
            int newRetries = event.getRetries() + 1;
            outboxEventMapper.markRetryOrFailed(event.getId(), newRetries, MAX_RETRIES);
            if (newRetries >= MAX_RETRIES) {
                log.error("outbox 事件发送失败，已达最大重试次数: id={}, eventType={}", event.getId(), event.getEventType(), e);
            } else {
                log.warn("outbox 事件发送失败，将重试 (retries={}): id={}, eventType={}", newRetries, event.getId(), event.getEventType(), e);
            }
        }
    }
}
