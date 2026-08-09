## Purpose
文件存储：分片上传、断点续传、秒传、租户内去重、Range 下载、配额扣减一致性、上传安全。

## Requirements

### Requirement: 分片上传
系统 SHALL 支持大文件分片上传：客户端请求初始化 → 服务端返回 uploadId 与各分片预签名 URL → 客户端直传 MinIO → 客户端通知合并。分片大小默认 5MB，可配置。分片状态主存 Redis，PG `file_chunk` 表持久化兜底。

#### Scenario: 初始化分片上传
- **WHEN** 客户端对 100MB 文件请求上传（带 total_size、chunk_size、hash 可选）
- **THEN** 返回 uploadId 与 20 个分片预签名 PUT URL（TTL 15min）

#### Scenario: 合并分片
- **WHEN** 客户端通知所有分片上传完成
- **THEN** 服务端校验所有分片存在于 MinIO，调用 MinIO compose 合并对象，写 file_meta + file_content，扣配额，发 upload.completed 事件

#### Scenario: 分片缺失
- **WHEN** 合并时发现某分片未上传
- **THEN** 返回 400（错误码 CHUNK_MISSING），列出缺失分片索引，允许客户端补传

### Requirement: 断点续传
系统 SHALL 支持断点续传：客户端用 uploadId 查询已上传分片列表，仅上传缺失分片。

#### Scenario: 查询已上传分片
- **WHEN** 客户端用有效 uploadId 查询进度
- **THEN** 返回已上传分片索引列表与总片数

#### Scenario: 续传
- **WHEN** 客户端仅上传缺失分片后请求合并
- **THEN** 系统按完整分片集合合并，行为同首次合并

### Requirement: 秒传
客户端上传前 SHALL 提交文件内容 hash（SHA-256）。若当前租户内已存在相同 hash 的 file_content，系统 MUST 不传输字节，直接创建新 file_meta 引用并 ref_count+1。

#### Scenario: 秒传命中
- **WHEN** 客户端提交 hash，且当前租户 file_content 表已存在该 hash
- **THEN** 不签发任何预签名 URL，直接创建 file_meta（content_ref 指向已有对象），ref_count+1，扣配额，返回 201

#### Scenario: 秒传未命中
- **WHEN** hash 在当前租户内不存在
- **THEN** 进入正常分片上传流程

### Requirement: 租户内去重
file_content 表 SHALL 对 `(tenant_id, hash)` 建唯一约束。引用计数 `ref_count` 记录指向同一物理对象的 file_meta 数。去重 MUST 仅在租户内生效，跨租户不共享物理对象。

#### Scenario: 同租户重复上传
- **WHEN** 租户 T1 用户 A 已上传 hash=H，用户 B 再上传相同 hash 文件
- **THEN** 复用同一 MinIO 对象，ref_count=2，两用户各持一份 file_meta

#### Scenario: 跨租户不共享
- **WHEN** 租户 T1 已有 hash=H，租户 T2 用户上传相同 hash
- **THEN** T2 触发正常上传，创建独立物理对象，T1 的 ref_count 不变

### Requirement: Range 下载
系统 SHALL 支持通过预签名 GET URL 下载文件，MinIO 原生支持 Range 请求。业务服务不承流，仅签发短 TTL URL（5min）。

#### Scenario: 下载文件
- **WHEN** 用户请求下载 file_id 对应文件
- **THEN** 返回 302 重定向到 MinIO 预签名 GET URL（TTL 5min）

#### Scenario: Range 请求
- **WHEN** 客户端用 Range 头请求部分内容
- **THEN** MinIO 返回 206 Partial Content 与对应字节范围

### Requirement: 配额扣减一致性
上传成功 MUST 通过本地事务表 + RabbitMQ 事务消息异步扣减配额，保证最终一致。扣减 MUST 幂等（按 file_meta.id 去重）。失败重试至成功或进死信队列告警。

#### Scenario: 正常扣减
- **WHEN** file_meta 写入成功，outbox 事件发送
- **THEN** user-service 消费事件，原子地 `update tenant set used = used + size where id = ? and used + size <= quota`，标记事件已处理

#### Scenario: 配额超限回滚
- **WHEN** 扣减时发现 used + size > quota（并发竞争）
- **THEN** user-service 发补偿事件，core-service 删除 file_meta + MinIO 对象，notification 发配额超限告警

### Requirement: 上传安全
系统 MUST 校验：文件名不含路径穿越字符（`../`、`..\`、绝对路径）；扩展名在白名单内；单文件大小不超过租户剩余配额与全局上限（默认 5GB）；分片大小合法。

#### Scenario: 路径穿越拒绝
- **WHEN** 文件名为 `../etc/passwd`
- **THEN** 返回 400（错误码 INVALID_FILENAME），不创建任何记录

#### Scenario: 类型白名单
- **WHEN** 上传 `.exe` 文件且白名单不含该扩展
- **THEN** 返回 400（错误码 FILE_TYPE_NOT_ALLOWED）
