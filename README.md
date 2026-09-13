## README

CloudStorage 是一个多租户云存储系统，支持文件上传下载、秒传去重、分享、多语言，二期叠加 RAG 知识库检索。

### 架构

- **后端**: Spring Cloud 微服务 (gateway / user / core / notification)
- **前端**: React + TypeScript + Antd + Tailwind + Zustand
- **基础设施**: Postgres(+pgvector) / Redis / RabbitMQ / MinIO / Nacos / SkyWalking

### 快速启动（本地开发）

```bash
# 1. 仅启动基础设施（前后端服务属于 app profile，不会启动）
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

### Docker 部署

前后端服务在 `app` profile 下，只有指定 profile 时才会构建/启动，`docker compose up -d` 默认只启动基础设施。

一键构建并启动全部基础设施 + 前后端：

```bash
docker compose --profile app up -d --build
```

启动后入口：

| 服务 | 地址 | 凭据 |
|---|---|---|
| 前端 (Nginx) | http://localhost | 静态资源 + `/api` 反向代理 |
| 网关 gateway-service | http://localhost:8080 | |
| user-service | http://localhost:8081 | |
| core-service | http://localhost:8082 | |
| notification-service | http://localhost:8083 | |
| Nacos | http://localhost:8848/nacos | nacos / nacos |
| RabbitMQ | http://localhost:15672 | cloudstorage / cloudstorage123 |
| MinIO Console | http://localhost:9001 | minioadmin / minioadmin123 |

镜像说明：

- `cloudstorage-backend`：多阶段构建（Maven 构建 + JRE 运行），一个镜像内含四个服务的可执行 jar，
  容器通过环境变量 `SERVICE` 选择启动的服务（见 `Dockerfile`）。
- `cloudstorage-frontend`：多阶段构建（Node/pnpm 构建 + Nginx 托管），Nginx 同时将 `/api/`
  反向代理到 `gateway-service:8080`（见 `frontend/Dockerfile`、`frontend/nginx.conf`）。

配置要点：

- 数据库 / Redis / RabbitMQ / Nacos / JWT 等通过 compose 环境变量注入，优先级高于 Nacos 中的同名配置，
  因此导入的 `cloudstorage-common.yaml` 无需再为容器修改地址。
- MinIO 预签名 URL 由 `MINIO_PUBLIC_ENDPOINT`（默认 `http://localhost:9000`）生成，保证浏览器可直接访问；
  容器内部访问 MinIO 仍使用 `MINIO_ENDPOINT=http://minio:9000`。
  部署到其他主机时将对外地址改为该主机可达地址，例如：
  ```bash
  MINIO_PUBLIC_ENDPOINT=http://your-host:9000 docker compose up -d --build
  ```
- 数据卷持久化在 `./data` 下（postgres / redis / rabbitmq / minio / nacos）。

常用命令：

```bash
docker compose ps                                 # 查看基础设施状态
docker compose --profile app ps                   # 查看全部（含前后端）
docker compose logs -f gateway-service            # 查看日志（需先启用 app profile）
docker compose --profile app up -d --build core-service   # 重新构建单个服务
docker compose down                               # 停止基础设施
docker compose --profile app down                 # 停止全部
```

单独构建镜像：

```bash
docker build -t cloudstorage-backend .
docker build -t cloudstorage-frontend ./frontend
```

> 首次构建需下载 Maven / pnpm 依赖，耗时较长；后续构建会命中缓存。

> 若网络无法访问 Docker Hub（如国内环境），可先配置镜像加速，或通过环境变量替换基础镜像后再构建：
> ```bash
> MAVEN_IMAGE=docker.m.daocloud.io/library/maven:3.9-eclipse-temurin-21 \
> JRE_IMAGE=docker.m.daocloud.io/library/eclipse-temurin:21-jre \
> NODE_IMAGE=docker.m.daocloud.io/library/node:22-alpine \
> NGINX_IMAGE=docker.m.daocloud.io/library/nginx:1.27-alpine \
> docker compose --profile app up -d --build
> ```

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
