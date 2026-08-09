## Context

全新空项目（仅 IntelliJ 生成的 `Main.java` + 空 `pom.xml`，Java 21）。需从零搭建一个多租户网盘 MVP，后端 Spring Cloud 微服务，前端 React SPA。二期将叠加 RAG，故一期须在数据模型（pgvector 预留、`tenant_id`）、架构（服务边界、事件流）、接口（`EmbeddingProvider` SPI 占位）上预留扩展点。

技术栈已锁定：Spring Cloud + Nacos（注册+配置）+ Spring Cloud Gateway + MyBatis-Plus + Postgres(+pgvector) + Redis + RabbitMQ + MinIO + SkyWalking；前端 React + TS + Antd + Tailwind + Zustand + i18next。部署以本地开发为主，后期 Docker 镜像；CI/CD 暂不做。本地开发用单机 Docker Compose 编排三方服务。

## Goals / Non-Goals

**Goals:**
- 4 服务微服务骨架可独立启动、互相通过 Nacos 发现
- 多租户 SaaS 模式：注册即建个人租户，`tenant_id` 全局自动过滤，MinIO bucket/前缀隔离
- 文件元数据/目录树/上传下载/秒传/租户内去重/分享/通知 全链路打通
- 高并发：分片上传 + 预签名 URL（字节不经过业务服务）+ Redis 缓存/锁 + MQ 削峰
- 高可用：各服务可水平扩展，Nginx upstream，Sentinel 限流熔断（一期落地，集群化二期）
- 多语言：后端 `Accept-Language` + MessageSource + 用户偏好覆盖；前端 i18next 中/英
- 亮/暗主题前端，虚拟滚动大目录，自定义分片上传组件
- SkyWalking 后端全链路追踪
- 测试基线：JUnit5+Mockito+Testcontainers / RestAssured / Playwright，JaCoCo ≥ 70%
- 二期 RAG 预留：pgvector 扩展启用、`tenant_id` 列、`EmbeddingProvider` SPI 占位、`knowledge_base` 表预留

**Non-Goals:**
- 团队租户 / 角色权限矩阵（二期）
- 真正的生产级集群（PG 主从、Redis 哨兵、Nacos 集群）——一期只验证可扩展性
- RAG 实现（二期）
- 缩略图 / 视频转码 / 在线预览（二期）
- 日志聚合（ELK/Loki）、指标（Prometheus）——一期只做 trace
- CI/CD 流水线
- 移动端 / 桌面客户端

## Decisions

### D1. 服务拆分：4 服务
`gateway-service` / `user-service` / `core-service`(file+storage+share 三模块) / `notification-service`。
- **为何**：纯 6 服务拆分一期过重；合并 file+storage+share 减少 RPC 与分布式事务面；notification 独立以便异步解耦、单独扩展、二期复用做 RAG 索引通知。
- **备选**：6 服务全拆（过度设计）/ 单体（无法水平扩展上传压力）。

### D2. 数据库：Postgres + pgvector（共享库 + `tenant_id` 列）
- **为何**：pgvector 让二期 RAG 复用同一实例，省一套向量库；MyBatis-Plus 多租户插件自动注入 `tenant_id`，改造成本低；共享库+行级隔离适合 SaaS 起步。
- **备选**：MySQL（无 pgvector，二期需引入向量库）/ Schema-per-tenant（运维复杂，过度设计）。
- **主键**：`bigint identity` + MyBatis-Plus 雪花 ID（分布式安全、索引友好）。

### D3. 多租户隔离强度：共享库 + 行级
- **为何**：本地开发/SaaS 起步最省事；MyBatis-Plus 多租户插件拦截 SQL 自动加 `WHERE tenant_id`。
- **MinIO 隔离**：bucket 或 key 前缀按租户（一期用前缀 `tenant/{tenantId}/...`，bucket 二期再分）。
- **Redis key 前缀**：`t:{tenantId}:...`。

### D4. 文件去重：租户内去重（跨租户不共享）
- **为何**：跨租户共享物理文件有隐私/合规风险（A 租户能通过秒传探测 B 租户是否存过某文件）。
- **实现**：`file_content` 表 `(tenant_id, hash)` 唯一键 + `ref_count`；秒传时同租户命中则 ref+1 不传字节。

### D5. 上传下载：分片 + 断点续传 + 秒传 + 预签名 URL
- **分片状态**：Redis 主存 `upload:{uploadId}` → 分片 bitmap + 各片 hash；PG 落 `file_chunk` 表做持久化兜底。
- **字节流**：前端直连 MinIO 预签名 URL 上传/下载，业务服务只签 URL 不承流 → 高并发关键。
- **Range 下载**：MinIO 原生支持，业务服务签 URL 后前端重定向。
- **备选**：字节经过业务服务（吞吐瓶颈）/ 客户端直传无预签名（密钥泄露）。

### D6. 配额扣减：本地事务表 + RabbitMQ 事务消息（最终一致性）
- **为何**：跨 storage(写 MinIO) / file(写元数据) / user(扣配额) 三模块，2PC 不可行；用本地事务表 + MQ 可靠消息做最终一致。
- **流程**：上传完成 → core-service 本地事务写 `file_meta` + `outbox` 事件 → MQ → user-service 扣配额（幂等）+ notification 发通知。失败重试 + 死信告警。

### D7. 目录树：物化路径 + parent_id 双模型
- **为何**：parent_id 对单层查询/移动友好；物化路径（`/a/b/c/`）对递归子树查询友好；双写成本可接受。
- **移动**：更新子树物化路径前缀 + Redis 分布式锁防并发移动。

### D8. 多语言：`Accept-Language` + MessageSource + 用户偏好覆盖
- 请求头 `Accept-Language` → `LocaleResolver` → `MessageSource(messages_zh/messages_en)`。
- user 表 `locale` / `theme` 列；登录后用户偏好覆盖请求头。
- 错误码统一 i18n，前端按 code 渲染。

### D9. 链路追踪：SkyWalking 后端全链路
- agent 接入 4 服务 + MySQL/Redis/MinIO/RabbitMQ 插件；前端埋点二期。
- **为何**：SkyWalking 对 Spring Cloud 生态集成成熟，无侵入 agent。

### D10. 二期 RAG 预留
- pgvector 扩展一期启用；`knowledge_base`、`kb_chunk` 表一期建表预留（带 `tenant_id`）。
- `EmbeddingProvider` SPI 接口在 `common` 模块定义，一期不实现；二期三实现：Ollama / OpenAI / Anthropic。
- `core-service` 留 `kb` 模块空包占位。

### D11. 前端架构
- Vite + TS + Antd + Tailwind + Zustand + i18next + react-router。
- 主题：CSS 变量 + Antd ConfigProvider；语言：i18next + 后端 `Accept-Language` 联动。
- 大目录：`react-virtual` 虚拟滚动；上传组件自研（antd Upload 不支持分片+秒传）。
- 通知：SSE/WebSocket 二期，一期用轮询。

## Risks / Trade-offs

- **[MinIO 单点]** → 一期单实例够用；二期可 MinIO 纠删码多盘 / 分布式模式。
- **[多租户行级隔离越权]** → MyBatis-Plus 多租户插件 + 网关 `X-Tenant-Id` 强校验 + 单测覆盖所有查询路径；手动 SQL 必须带 `tenant_id`，CI 加 SQL 审查。
- **[去重 ref_count 并发]** → Redis 分布式锁包裹"查 hash + 建引用 + ref+1"，DB 层乐观锁兜底。
- **[事务消息落地复杂度]** → 一期先实现本地事务表 + 定时扫描重发；若复杂度过高可降级为"上传成功后同步扣配额 + 失败补偿"。
- **[大目录物化路径更新开销]** → 限制单目录深度与子节点数；超大批量移动走异步 MQ。
- **[SkyWalking agent 与 Java 21 兼容]** → 选用 ≥ 9.2 版本 agent；启动失败时降级为关闭 trace 不阻塞业务。
- **[预签名 URL 泄露]** → 短 TTL（5-15min）+ 单次性 + 绑定租户/IP（二期）。
- **[前端分片上传浏览器兼容]** → 明确支持现代浏览器（Chrome/Edge/Firefox 最新两版），不支持 IE。
- **[二期 RAG 数据规模]** → pgvector 单表亿级检索需 IVFFlat/HNSW 索引；一期建表时即建索引占位。
