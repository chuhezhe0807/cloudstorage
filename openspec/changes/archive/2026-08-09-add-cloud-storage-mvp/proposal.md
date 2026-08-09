## Why

需要一个支持高并发、高可用的多租户网盘系统，提供文件上传下载、秒传去重、分享等核心能力，并为二期 RAG（选中文件/文件夹作为知识库检索）打好数据与架构基础。一期聚焦网盘 MVP 与前端体验，二期再叠加 RAG。

## What Changes

- 新增 Spring Cloud 微服务网盘后端，拆分为 4 个服务：`gateway-service`、`user-service`、`core-service`（file+storage+share 合一）、`notification-service`
- 新增多租户能力：SaaS 模式，用户注册自动创建个人租户，所有业务表带 `tenant_id` 列；MinIO bucket/前缀按租户隔离；团队租户留待二期
- 新增认证授权：JWT + 刷新令牌，网关统一鉴权与多租户头注入
- 新增文件元数据与目录树：物化路径 + parent_id 双模型，支持移动/重命名/回收站/搜索
- 新增上传下载核心：分片上传、断点续传、秒传、租户内去重（引用计数）、Range 下载、预签名 URL、配额扣减（事务消息）
- 新增分享能力：分享链接、提取码、过期时间、下载次数限制
- 新增通知能力：RabbitMQ 异步事件消费 + 站内信持久化
- 新增多语言：后端 `Accept-Language` + MessageSource + 用户偏好覆盖；前端 i18next 中/英
- 新增前端 SPA：React + TS + Antd + Tailwind + Zustand，亮/暗主题，虚拟滚动文件浏览器，自定义分片上传组件
- 新增可观测性：SkyWalking 后端全链路追踪
- 新增测试基线：JUnit5 + Mockito + Testcontainers 单元/集成测试，RestAssured 接口测试，Playwright E2E，JaCoCo 70% 覆盖率门槛
- 数据库从 MySQL 调整为 **Postgres + pgvector**（一期仅启用 pgvector 扩展预留，二期 RAG 复用）
- **BREAKING**：项目从空骨架起步，无既有 API 兼容性问题

二期（不在本次范围内，仅留接口与数据预留）：
- `EmbeddingProvider` 抽象（Ollama / OpenAI / Anthropic 三实现）
- 文档解析 → 切块 → Embedding → pgvector（带 `tenant_id`）
- knowledge_base 实体与建库、增量/重建索引、检索 API 与对话页

## Capabilities

### New Capabilities

- `tenant`: 多租户隔离与租户生命周期（SaaS 个人租户、注册即建、`tenant_id` 全局过滤）
- `auth`: 用户注册/登录/JWT/刷新令牌/语言主题偏好
- `file`: 文件元数据与目录树（CRUD、移动、重命名、回收站、搜索）
- `storage`: 上传下载（分片、断点续传、秒传、租户内去重、Range 下载、预签名 URL、配额）
- `share`: 分享链接（提取码、过期、下载次数限制）
- `notification`: 异步事件消费与站内信
- `i18n`: 后端多语言（Accept-Language + MessageSource + 用户偏好覆盖）
- `frontend`: React SPA（主题、语言、文件浏览器、上传组件、分享弹窗）

### Modified Capabilities

无（全新项目）。

## Impact

- **代码**：全新 Maven 多模块工程，替换现有空 `Main.java` 骨架；模块 `cloudstorage-parent` / `common` / `gateway-service` / `user-service` / `core-service` / `notification-service`
- **API**：新增网关对外 REST API（鉴权、文件、分享、通知），统一响应体与错误码 i18n
- **依赖（三方服务，Docker 部署）**：Nacos（注册+配置）、Postgres(+pgvector)、Redis、RabbitMQ、MinIO、SkyWalking OAP+UI
- **数据库**：Postgres 共享库 + `tenant_id` 列，MyBatis-Plus 多租户插件自动注入；主键 bigint identity + 雪花 ID
- **前端**：新建独立前端工程，Vite + TS
- **可观测性**：SkyWalking agent 接入后端 4 服务
- **安全**：路径穿越/XSS 防护、上传类型大小白名单、分享密码+过期+次数、预签名防盗链、HTTPS
- **部署**：本地开发为主，后期 Docker 镜像；CI/CD 暂不做
