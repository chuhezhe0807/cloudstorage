package com.chuhezhe.common.mq;

public interface RabbitMqConstants {

    String EXCHANGE = "cloudstorage.events";

    String NOTIFICATION_QUEUE = "cloudstorage.notifications";
    String NOTIFICATION_DLQ = "cloudstorage.notifications.dlq";
    String NOTIFICATION_ROUTING_KEY = "notification.#";
    String NOTIFICATION_DLQ_ROUTING_KEY = "notification.dlq";

    String KB_INDEX_QUEUE = "cloudstorage.kb.index";
    String KB_INDEX_ROUTING_KEY = "kb.index.request";
}
