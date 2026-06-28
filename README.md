## README

CloudStorage 是一个多租户云存储系统，支持文件上传下载、秒传去重、分享、多语言，二期叠加 RAG 知识库检索。

### 架构

- **后端**: Spring Cloud 微服务 (gateway / user / core / notification)
- **前端**: React + TypeScript + Antd + Tailwind + Zustand
- **基础设施**: Postgres(+pgvector) / Redis / RabbitMQ / MinIO / Nacos / SkyWalking

### 快速启动

```bash
# 1. 启动基础设施
docker compose up -d

# 2. Nacos 控制台 (http://localhost:8848/nacos, nacos/nacos)
#    导入配置: dataId=cloudstorage-common.yaml, group=DEFAULT_GROUP
#    内容见 docs/nacos-cloudstorage-common.yaml

# 3. 编译后端
mvn clean compile -DskipTests

# 4. 按顺序启动服务
#    gateway-service     :8080
#    user-service        :8081
#    core-service        :8082
#    notification-service:8083

# 5. 启动前端
cd frontend && npm install && npm run dev
#    前端 http://localhost:5173，自动代理 /api 到 :8080
```

### 测试

```bash
mvn clean verify    # 运行全部测试，JaCoCo 覆盖率门槛 70%
mvn clean test      # 仅运行测试
```

### 模块

| 模块 | 职责 |
|---|---|
| common | 统一响应体/异常/i18n/JWT/多租户插件/SPI |
| gateway-service | Nacos 路由/JWT 鉴权/Sentinel 限流/CORS |
| user-service | 注册/登录/令牌刷新/用户偏好 |
| core-service | 文件元数据/目录树/上传下载/秒传/分享 |
| notification-service | MQ 消费/站内信 |
| frontend | React SPA |

### 关键设计

- **多租户**: 共享库+tenant_id列，MyBatis-Plus插件自动注入
- **文件去重**: 租户内按SHA-256去重，引用计数
- **秒传**: 预传hash，命中直接建引用
- **分片上传**: 前端直传MinIO，预签名URL
- **i18n**: JWT带locale → Gateway注入X-Locale头 → 后端LocaleResolver
- **事件驱动**: outbox_pattern → RabbitMQ → notification-service
