## Purpose
多租户能力：租户自动创建、租户上下文隔离、网关注入租户头、MinIO 按租户隔离、租户配额。

## Requirements

### Requirement: 租户自动创建
用户注册时系统 SHALL 自动创建一个个人租户，并将该用户设为租户所有者。租户名默认与用户名相同，可在注册后修改。

#### Scenario: 新用户注册自动建租户
- **WHEN** 一个新用户提交注册（账号、密码）成功
- **THEN** 系统在同一事务内创建一条 `tenant` 记录（类型=personal），并创建 `user` 记录（role=owner，tenant_id 指向新租户）

#### Scenario: 租户创建原子性
- **WHEN** 注册过程中租户创建成功但用户记录写入失败
- **THEN** 整个注册事务回滚，不残留孤儿租户

### Requirement: 租户上下文隔离
所有业务数据访问 SHALL 通过 `tenant_id` 进行行级隔离。MyBatis-Plus 多租户插件 MUST 自动为所有业务 SQL 注入 `WHERE tenant_id = ?` 条件，禁止任何业务 SQL 跨租户读写。

#### Scenario: 查询自动带租户过滤
- **WHEN** 用户 A（租户 T1）查询文件列表
- **THEN** 生成的 SQL 必须包含 `WHERE tenant_id = T1`，且不会返回租户 T2 的任何文件

#### Scenario: 跨租户访问被拒绝
- **WHEN** 用户 A 试图通过伪造文件 ID 访问租户 T2 的文件
- **THEN** 系统返回 404 Not Found（不泄露资源存在性），并记录安全审计日志

### Requirement: 网关注入租户头
网关 SHALL 从 JWT 中解析 `tenant_id` 并注入 `X-Tenant-Id` 请求头下发给下游服务；下游服务 MUST 信任并使用该头初始化 `TenantContext`，且 MUST 校验该头与用户 JWT 中的 tenant 一致。

#### Scenario: 正常请求带租户头
- **WHEN** 已登录用户携带有效 JWT 访问 `/api/files`
- **THEN** 网关解析 JWT 得到 tenant_id，转发请求时携带 `X-Tenant-Id`，core-service 用该值初始化 TenantContext

#### Scenario: 缺失或伪造租户头
- **WHEN** 下游服务收到无 `X-Tenant-Id` 头、或该头值与 JWT 中 tenant 不一致的请求
- **THEN** 服务返回 401 Unauthorized，拒绝处理

### Requirement: MinIO 按租户隔离
文件对象在 MinIO 中 SHALL 按 `tenant/{tenantId}/...` 前缀隔离；预签名 URL MUST 绑定到对应租户前缀，禁止跨租户访问对象。

#### Scenario: 上传对象落入租户前缀
- **WHEN** 租户 T1 用户上传文件 `a.txt`
- **THEN** 对象 key 形如 `tenant/T1/{snowflakeId}/a.txt`，租户 T2 无法通过其预签名 URL 访问该对象

### Requirement: 租户配额
每个租户 SHALL 拥有总存储配额（默认 1GB，可由租户所有者调整）。所有上传（含秒传新增引用）MUST 累加占用，删除/清空回收站 MUST 释放配额。

#### Scenario: 配额超限拦截
- **WHEN** 租户 T1 已用 1GB，用户尝试上传 10MB 文件
- **THEN** 上传前置校验返回 413 Payload Too Large（错误码 QUOTA_EXCEEDED），不创建任何分片记录

#### Scenario: 删除释放配额
- **WHEN** 用户永久删除一个 100MB 文件（引用计数归零，物理对象删除）
- **THEN** 租户已用配额减少 100MB
