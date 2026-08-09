## ADDED Requirements

### Requirement: LLM 与 Embedding 配置
系统 SHALL 通过 Nacos 配置注入 LLM 访问参数（`llm.provider`、`llm.base-url`、`llm.api-key`、`llm.chat-model`、`llm.embedding-model`、`llm.embedding-dimension`）。`api-key` MUST 不入日志、不回显到前端。`EmbeddingProvider` 实现 SHALL 走 OpenAI 兼容协议调用 `{base-url}/embeddings`，返回维度与 `embedding-dimension` 一致。

#### Scenario: 配置注入
- **WHEN** core-service 启动并加载 Nacos `cloudstorage-common.yaml`
- **THEN** `LlmProperties` 绑定 `llm.*` 配置项，api-key 由 `${LLM_API_KEY}` 环境变量/Nacos 加密配置解析

#### Scenario: Embedding 调用
- **WHEN** 向量化消费者调用 `EmbeddingProvider.embedBatch(["文本1","文本2"])`
- **THEN** 服务 POST `{base-url}/embeddings`（model=embedding-model, Authorization=Bearer api-key），返回 1536 维向量列表

#### Scenario: 密钥脱敏
- **WHEN** 系统打印 LLM 客户端配置或请求日志
- **THEN** api-key 以 `***` 脱敏，不出现明文

### Requirement: Langfuse 观测接入
系统 SHALL 通过 docker-compose 部署 langfuse（web 端口 3000，复用 Postgres 建 `langfuse` 库）。langfuse 配置（`langfuse.base-url`、`langfuse.public-key`、`langfuse.secret-key`）SHALL 经 Nacos 注入。RAG 对话编排 MUST 通过 langfuse SDK 上报 trace（至少覆盖 retrieve 与 generate 节点）。

#### Scenario: langfuse 启动
- **WHEN** 执行 `docker compose up -d`
- **THEN** langfuse 容器启动，http://localhost:3000 可访问管理界面，连接独立 `langfuse` 数据库

#### Scenario: trace 上报
- **WHEN** 一次 RAG 对话完成
- **THEN** langfuse SDK 用 public-key/secret-key 上报 trace，langfuse UI 可见该 trace 及其节点 span

#### Scenario: langfuse 不可用降级
- **WHEN** langfuse 服务不可达
- **THEN** 对话编排不阻塞，trace 上报失败仅记录 WARN 日志，不影响回答生成

### Requirement: HNSW 向量索引与类型处理
系统 SHALL 为 `kb_chunk.embedding` 建立 HNSW 余弦相似索引（`USING hnsw (embedding vector_cosine_ops)`）。`common` SHALL 提供 `VectorTypeHandler` 实现 MyBatis-Plus/JDBC 的 `float[]` ↔ pgvector 文本格式互转。检索 SQL MUST 使用 `<=>` 余弦距离算子走 HNSW 索引。

#### Scenario: 索引建立
- **WHEN** 执行 init-db.sql
- **THEN** `kb_chunk` 表存在 `USING hnsw (embedding vector_cosine_ops)` 索引

#### Scenario: 向量写入读取
- **WHEN** 服务向 kb_chunk 写入 embedding 为 float[1536]
- **THEN** VectorTypeHandler 将其转为 pgvector 文本格式存库，读回为 float[1536]

#### Scenario: 相似检索走索引
- **WHEN** 检索 SQL 执行 `ORDER BY embedding <=> CAST(? AS vector) LIMIT 5`
- **THEN** Postgres 走 HNSW 索引返回 top-5 余弦最近邻，不触发全表扫描