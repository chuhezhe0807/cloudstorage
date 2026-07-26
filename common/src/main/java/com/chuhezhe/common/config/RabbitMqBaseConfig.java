package com.chuhezhe.common.config;

import com.chuhezhe.common.mq.RabbitMqConstants;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(name = "org.springframework.amqp.core.TopicExchange")
public class RabbitMqBaseConfig {

    @Bean
    public TopicExchange eventExchange() {
        return new TopicExchange(RabbitMqConstants.EXCHANGE);
    }
}
