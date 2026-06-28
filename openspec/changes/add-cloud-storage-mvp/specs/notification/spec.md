## ADDED Requirements

### Requirement: 异步事件消费
notification-service SHALL 消费 RabbitMQ 事件并生成站内信。一期支持事件：`upload.completed`、`quota.exceeded`、`share.accessed`。消费 MUST 幂等（按事件 id 去重）。

#### Scenario: 上传完成通知
- **WHEN** core-service 发送 `upload.completed` 事件（userId, fileName, size）
- **THEN** notification-service 创建站内信（type=upload, 已读=false）存 PG，返回 ACK

#### Scenario: 重复消费幂等
- **WHEN** 同一事件 id 被消费两次
- **THEN** 仅创建一条站内信，第二次消费直接 ACK 不重复写入

### Requirement: 站内信持久化与查询
站内信 SHALL 持久化到 PG（带 `tenant_id`、`user_id`、`type`、`payload`、`read_at`、`created_at`）。用户可分页查询自己的站内信、标记已读、批量删除。

#### Scenario: 查询未读
- **WHEN** 用户调用 `GET /api/notifications?unread=true`
- **THEN** 返回当前租户内该用户 read_at 为空的站内信列表（分页，按 created_at 倒序）

#### Scenario: 标记已读
- **WHEN** 用户对 notification_id 调用 `PUT /api/notifications/{id}/read`
- **THEN** read_at 置当前时间，返回 200

### Requirement: 死信处理
消费失败超过重试上限（默认 3 次）的事件 MUST 进入死信队列，并记录告警日志。一期不自动恢复，需人工介入。

#### Scenario: 消费失败入死信
- **WHEN** 某事件消费 3 次均抛异常
- **THEN** 事件转入 DLQ，记录 ERROR 日志含事件 id 与异常栈，原始队列 ACK 不阻塞
