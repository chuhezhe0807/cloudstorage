package com.chuhezhe.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置：定义队列、交换机、死信队列、重试 3 次。
 */
@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE = "cloudstorage.events";
    public static final String QUEUE = "cloudstorage.notifications";
    public static final String DLQ = "cloudstorage.notifications.dlq";
    public static final String ROUTING_KEY = "notification.#";

    @Bean
    public TopicExchange eventExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(EXCHANGE)
                .deadLetterRoutingKey("notification.dlq")
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding notificationBinding() {
        return BindingBuilder.bind(notificationQueue())
                .to(eventExchange())
                .with(ROUTING_KEY);
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(eventExchange())
                .with("notification.dlq");
    }
}
