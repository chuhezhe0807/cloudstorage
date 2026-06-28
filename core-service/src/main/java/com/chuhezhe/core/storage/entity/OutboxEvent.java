package com.chuhezhe.core.storage.entity;

import com.chuhezhe.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 事务消息表：上传完成 / 配额变动等事件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("outbox_event")
public class OutboxEvent extends BaseEntity {

    private String aggregateId;
    private String eventType;
    private String payload;
    private String status;
    private Integer retries;
}
