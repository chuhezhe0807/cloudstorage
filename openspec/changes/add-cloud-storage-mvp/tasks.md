## 1. 地基与脚手架

- [x] 1.1 编写 `docker-compose.yml`：Nacos、Postgres(+pgvector 扩展)、Redis、RabbitMQ、MinIO、SkyWalking OAP+UI，本地一键起
- [x] 1.2 创建 Maven 多模块父工程 `cloudstorage-parent`：统一依赖版本管理（Spring Cloud / Cloud Alibaba / MyBatis-Plus / JWT / MinIO SDK / SkyWalking agent / Lombok / MapStruct）、Java 21、Checkstyle 配置
- [x] 1.3 创建 `common` 模块：统一响应体 `Result<T>`、全局异常体系与错误码枚举、i18n `MessageSource` 配置、`TenantContext`、雪花 ID 配置、DTO 基类、常量
- [x] 1.4 各服务模块骨架：`gateway-service` / `user-service` / `core-service` / `notification-service`，均含启动类、`bootstrap.yml`(Nacos)、SkyWalking agent 接入配置
- [x] 1.5 Postgres 初始化脚本：启用 `pgvector` 扩展、`tenant` / `user` / `file_meta` / `file_content` / `file_chunk` / `share_link` / `notification` / `outbox_event` / `kb`(预留空表) 建表，所有业务表带 `tenant_id` 列与索引
- [x] 1.6 MyBatis-Plus 多租户插件配置：忽略表白名单、自动注入 `tenant_id` 条件、雪花 ID 注入器
- [x] 1.7 Nacos 配置命名空间与 dataId 规划，上传各服务 `application.yml` 到 Nacos
- [x] 1.8 编写 `AGENTS.md` 记录构建/测试命令（mvn verify、JaCoCo 报告路径），供后续会话使用

## 2. 网关与多租户基础设施

- [x] 2.1 gateway-service 路由配置：user / core / notification 三下游服务路由
- [x] 2.2 全局 JWT 鉴权过滤器：校验签名与过期、白名单（注册/登录/刷新/分享提取）放行、解析 tenantId 注入 `X-Tenant-Id` 头
- [x] 2.3 限流过滤器（Sentinel）：按用户/租户维度限流，超限返回 429
- [x] 2.4 统一错误响应与 i18n：网关层错误按 `Accept-Language` 解析消息
- [x] 2.5 下游服务 `TenantContext` 过滤器：从 `X-Tenant-Id` 头初始化 ThreadLocal、与 JWT 校验一致性、请求结束清理
- [x] 2.6 CORS 配置允许前端域名
- [x] 2.7 gateway 接口测试（RestAssured）：鉴权/白名单/租户头注入/限流

## 3. 用户与认证（user-service）

- [x] 3.1 注册接口 `POST /api/auth/register`：账号唯一校验、BCrypt 密码、同事务建 personal 租户 + owner 用户、签发 JWT
- [x] 3.2 登录接口 `POST /api/auth/login`：密码校验、5 次错误锁定（Redis 计数）、签发 access+refresh
- [x] 3.3 刷新接口 `POST /api/auth/refresh`：refresh 一次性旋转、重复使用撤销全部 refresh
- [x] 3.4 登出接口 `POST /api/auth/logout`：refresh 入 Redis 黑名单
- [x] 3.5 用户偏好接口 `GET/PUT /api/user/preferences`：locale / theme 读写
- [x] 3.6 Redis 会话与黑名单封装
- [x] 3.7 user-service 单元测试（JUnit5+Mockito）：注册/登录/刷新/偏好，覆盖率 ≥ 70%
- [x] 3.8 user-service 接口测试（RestAssured + Testcontainers PG/Redis）：全流程 + 错误码 i18n

## 4. 文件元数据与目录树（core-service / file 模块）

- [x] 4.1 file_meta / file_content 实体与 Mapper，物化路径 + parent_id 双字段
- [x] 4.2 目录创建接口 `POST /api/files/mkdir`
- [x] 4.3 列表接口 `GET /api/files?parentId=`：Redis 缓存目录列表，租户过滤
- [x] 4.4 重命名接口 `PATCH /api/files/{id}/rename`：同目录同名冲突校验
- [x] 4.5 移动接口 `PATCH /api/files/{id}/move`：Redis 分布式锁、子树物化路径前缀原子更新、锁超时 409
- [x] 4.6 软删除接口 `DELETE /api/files/{id}`：置 deleted_at
- [x] 4.7 回收站列表/还原/永久删除接口：永久删除联动 MinIO 对象 + ref_count + 配额释放
- [x] 4.8 搜索接口 `GET /api/files/search`：名称模糊 + 类型/大小/时间过滤 + 分页
- [x] 4.9 回收站自动清理定时任务（30 天到期永久删除）
- [x] 4.10 file 模块单元测试 + 接口测试（Testcontainers PG/Redis/MinIO）

## 5. 上传下载（core-service / storage 模块）

- [x] 5.1 MinIO 客户端封装：bucket 初始化、租户前缀工具、预签名 PUT/GET URL 签发
- [x] 5.2 秒传接口 `POST /api/storage/check-hash`：当前租户内 hash 命中则建 file_meta + ref_count+1 + 扣配额
- [x] 5.3 分片上传初始化 `POST /api/storage/upload/init`：生成 uploadId、签发分片预签名 URL、Redis 存分片 bitmap
- [x] 5.4 分片上传进度查询 `GET /api/storage/upload/{uploadId}`：返回已传分片索引
- [x] 5.5 合并接口 `POST /api/storage/upload/{uploadId}/complete`：校验分片完整、MinIO compose、写 file_meta+file_content、outbox 事件、扣配额
- [x] 5.6 断点续传：复用 5.3/5.4/5.5，缺失分片补传
- [x] 5.7 下载接口 `GET /api/storage/download/{fileId}`：签发预签名 GET URL，302 重定向，TTL 5min
- [x] 5.8 上传安全校验：文件名路径穿越、扩展名白名单、大小上限、分片大小
- [x] 5.9 配额扣减最终一致：outbox 表 + RabbitMQ 事务消息，user-service 幂等消费、超限补偿回滚
- [x] 5.10 upload.completed / quota.exceeded 事件发送
- [x] 5.11 storage 模块单元测试 + 接口测试

## 6. 分享（core-service / share 模块）

- [x] 6.1 share_link 实体与 Mapper
- [x] 6.2 创建分享 `POST /api/shares`：可选提取码（默认生成 6 位）、expire_at、max_downloads、code ≥ 32 位随机
- [x] 6.3 访问分享 `POST /api/shares/{code}/access`：提取码校验 + 锁定限流、过期/次数校验、返回预签名 URL 或目录快照
- [x] 6.4 我的分享列表 `GET /api/shares`、取消 `DELETE /api/shares/{id}`、更新 `PATCH /api/shares/{id}`
- [x] 6.5 share 模块单元测试 + 接口测试

## 7. 通知（notification-service）

- [x] 7.1 RabbitMQ 配置：exchange / queue / DLQ、重试 3 次
- [x] 7.2 事件消费者：upload.completed / quota.exceeded / share.accessed，幂等（事件 id 去重）
- [x] 7.3 站内信持久化实体与 Mapper
- [x] 7.4 查询接口 `GET /api/notifications`（未读过滤/分页）、标记已读 `PUT /api/notifications/{id}/read`、批量删除
- [x] 7.5 notification 单元测试 + 接口测试

## 8. i18n 与安全加固

- [x] 8.1 `messages_zh.properties` / `messages_en.properties` 全错误码覆盖
- [x] 8.2 `LocaleResolver` 优先级：用户偏好 > Accept-Language > 默认 zh
- [x] 8.3 统一响应体 + 全局异常处理器输出 i18n message + args
- [x] 8.4 安全审查：路径穿越单测、XSS 过滤、上传类型白名单单测、HTTPS 配置文档、预签名 URL TTL 校验
- [x] 8.5 i18n 接口测试覆盖中/英/带参/回退

## 9. 前端脚手架

- [x] 9.1 Vite + React + TS 工程初始化，集成 Antd + Tailwind + Zustand + i18next + react-router
- [x] 9.2 axios 客户端封装：baseURL、`Accept-Language` 头注入、JWT 拦截器、refresh 自动续期、错误码 toast
- [x] 9.3 主题（亮/暗）+ 语言（中/英）全局 Provider，localStorage 持久化 + 登录后同步偏好
- [x] 9.4 路由守卫 + 全局错误边界 + 错误回退页
- [x] 9.5 登录/注册页（含表单校验、错误码 i18n）
- [x] 9.6 主布局：侧边栏 + 顶栏（主题/语言切换、用户菜单、通知入口）

## 10. 前端文件浏览器与上传下载

- [x] 10.1 文件列表页：面包屑、列表/网格切换、react-virtual 虚拟滚动
- [x] 10.2 右键菜单与操作弹窗：重命名、移动（目录选择树）、删除、分享、下载
- [x] 10.3 自研上传组件：拖拽 + 选择、crypto.subtle 算 SHA-256 调秒传、分片并发上传、断点续传、进度条、失败重试
- [x] 10.4 下载：处理 302 预签名重定向
- [x] 10.5 回收站页：列表、还原、永久删除
- [x] 10.6 搜索页：名称 + 过滤条件 + 分页
- [x] 10.7 分享创建弹窗 + 分享管理页 + 分享访问页（提取码输入）
- [x] 10.8 通知列表页 + 标记已读

## 11. 测试与质量门禁

- [ ] 11.1 JaCoCo 插件配置，覆盖率门槛 70%，`mvn verify` 失败即阻断
- [ ] 11.2 全部后端接口 RestAssured 用例补齐，关键路径 E2E（Playwright）
- [ ] 11.3 Playwright E2E：登录/上传/下载/分享/回收站/语言切换/主题切换
- [ ] 11.4 SkyWalking 全链路验证：上传链路 trace 完整可见
- [ ] 11.5 安全测试用例集：路径穿越 / 跨租户访问 / 提取码爆破 / 预签名 URL 过期

## 12. 可水平扩展与打磨

- [ ] 12.1 各服务多实例本地启动验证（不同端口）
- [ ] 12.2 Nginx upstream 配置 + 前端静态资源托管
- [ ] 12.3 Sentinel 限流熔断规则调优
- [ ] 12.4 JMeter 压测：分片上传并发、目录列表 QPS、下载带宽
- [ ] 12.5 文档：`README.md` 本地启动步骤、架构图、API 概览
- [ ] 12.6 JaCoCo 覆盖率达标复核

## 13. 二期 RAG 预留（仅占位，不实现）

- [x] 13.1 `common` 模块定义 `EmbeddingProvider` SPI 接口（embed/批量 embed），一期不实现
- [x] 13.2 `core-service` 留 `kb` 空包占位
- [x] 13.3 `knowledge_base` / `kb_chunk` 建表（带 `tenant_id`、pgvector 列）
- [x] 13.4 RabbitMQ 预留 `kb.index.request` exchange/queue（不接消费者）
