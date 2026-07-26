package com.chuhezhe.core.storage.entity;

import com.chuhezhe.common.entity.BaseEntity;
import com.chuhezhe.common.handler.JsonbTypeHandler;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 事务消息表：上传完成 / 配额变动等事件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "outbox_event", autoResultMap = true)
public class OutboxEvent extends BaseEntity {

    private String aggregateId;
    private String eventType;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String payload;
    private String status;
    private Integer retries;
}
