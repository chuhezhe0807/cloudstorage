# CloudStorage - Development Guide

## Prerequisites

- Java 21
- Docker & Docker Compose
- Maven 3.9+
- SkyWalking Java Agent (optional, for tracing)

## Quick Start

```bash
# 1. Start infrastructure
docker compose up -d

# 2. Start Nacos and import config
#    Visit http://localhost:8848/nacos
#    Create config: dataId=cloudstorage-common.yaml, group=DEFAULT_GROUP
#    Content: see docs/nacos-cloudstorage-common.yaml

# 3. Build all modules
mvn clean compile -DskipTests

# 4. Start services (in order)
#    gateway-service:  http://localhost:8080
#    user-service:     http://localhost:8081
#    core-service:     http://localhost:8082
#    notification-service: http://localhost:8083
```

## Build & Test Commands

```bash
# Compile all
mvn clean compile

# Run all tests
mvn clean test

# Run tests with coverage check (JaCoCo threshold: 70%)
mvn clean verify

# Skip tests
mvn clean package -DskipTests

# Build specific module
mvn clean package -pl user-service -am -DskipTests
```

## Coverage Report

After `mvn verify`, JaCoCo reports are at:
- `target/site/jacoco/index.html` (per module)
- Aggregated report: `mvn clean verify jacoco:report-aggregate`

## Infrastructure URLs

| Service        | URL                        | Credentials                  |
|----------------|----------------------------|------------------------------|
| Nacos Console  | http://localhost:8848/nacos | nacos / nacos               |
| Postgres       | localhost:5432             | cloudstorage / cloudstorage123 |
| Redis          | localhost:6379             | password: redis123           |
| RabbitMQ Admin | http://localhost:15672     | cloudstorage / cloudstorage123 |
| MinIO Console  | http://localhost:9001      | minioadmin / minioadmin123  |
| SkyWalking UI  | http://localhost:8081      | (no auth)                    |

## SkyWalking Agent Setup

Add JVM args to each service:
```
-javaagent:/path/to/skywalking-agent.jar
-Dskywalking.agent.service_name=user-service
-Dskywalking.collector.backend_service=127.0.0.1:11800
```

## Module Structure

```
cloudstorage/
├── common/                  Shared code (Result, Exception, i18n, TenantContext, SPI)
├── gateway-service/         Spring Cloud Gateway (auth, routing, rate-limit)
├── user-service/            Authentication, JWT, tenant, user preferences
├── core-service/            File meta, directory, upload/download, share
├── notification-service/    MQ consumer, in-app notifications
├── docker-compose.yml       Local infrastructure
└── scripts/
    └── init-db.sql          Postgres schema + pgvector extension
```

## Code Conventions

- Java 21, use records where appropriate
- Controllers delegate to Service interfaces; ServiceImpl contains business logic
- Mappers use MyBatis-Plus BaseMapper; complex queries use XML or LambdaQueryWrapper
- All DTOs use Lombok @Data; entities extend BaseEntity
- API response: `Result<T>` with i18n error codes from ErrorCode enum
- Tenant isolation: MyBatis-Plus plugin auto-injects `tenant_id`; ignore tables: tenant, user, share_link, outbox_event
