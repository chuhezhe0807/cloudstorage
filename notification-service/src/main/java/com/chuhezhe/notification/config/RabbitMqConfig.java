package com.chuhezhe.notification.config;

import com.chuhezhe.common.mq.RabbitMqConstants;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置：定义队列、交换机、死信队列、重试 3 次。
 */
@Configuration
public class RabbitMqConfig {

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(RabbitMqConstants.NOTIFICATION_QUEUE)
                .deadLetterExchange(RabbitMqConstants.EXCHANGE)
                .deadLetterRoutingKey(RabbitMqConstants.NOTIFICATION_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(RabbitMqConstants.NOTIFICATION_DLQ).build();
    }

    @Bean
    public Binding notificationBinding(Queue notificationQueue, TopicExchange eventExchange) {
        return BindingBuilder.bind(notificationQueue)
                .to(eventExchange)
                .with(RabbitMqConstants.NOTIFICATION_ROUTING_KEY);
    }

    @Bean
    public Binding dlqBinding(Queue deadLetterQueue, TopicExchange eventExchange) {
        return BindingBuilder.bind(deadLetterQueue)
                .to(eventExchange)
                .with(RabbitMqConstants.NOTIFICATION_DLQ_ROUTING_KEY);
    }

    /** 二期 RAG 索引请求队列（预留，当前不接消费者） */
    @Bean
    public Queue kbIndexQueue() {
        return QueueBuilder.durable(RabbitMqConstants.KB_INDEX_QUEUE).build();
    }

    @Bean
    public Binding kbIndexBinding(Queue kbIndexQueue, TopicExchange eventExchange) {
        return BindingBuilder.bind(kbIndexQueue)
                .to(eventExchange)
                .with(RabbitMqConstants.KB_INDEX_ROUTING_KEY);
    }
}
