## Context

一期 MVP 已交付完整云存储能力，并按 13.x 任务预留了 RAG 二期位：
- `knowledge_base` / `kb_chunk` 表（pgvector `embedding vector(1536)` 列）已建，但无相似索引、无应用代码。
- `common.spi.EmbeddingProvider` SPI 接口已定义（`embed/embedBatch/dimension`），无实现。
- MQ `cloudstorage.kb.index` 队列 + `kb.index.request` 路由键已声明，无消费者。
- `core-service` 下 `com.chuhezhe.core.kb` 空包占位（仅 `.gitkeep`）。
- Postgres 镜像已是 `pgvector/pgvector:pg16`，扩展已启用。

当前缺失：分片 + 向量化的异步编排、embedding/LLM 访问层、pgvector 检索（需 HNSW 索引 + JDBC 类型处理）、langgraph 对话编排、langfuse 观测、前端进度看板与对话页。业务约束：所有 RAG 数据按 `tenant_id` 行级隔离；LLM baseurl/apikey 须经 Nacos 注入、不入日志。

## Goals / Non-Goals

**Goals:**
- 以文件夹为粒度创建知识库，异步对该文件夹下所有文件分片 + 向量化，提供进度看板。
- 基于 langgraph 编排多轮 RAG 对话（检索 → 重排 → 生成 → 引用），支持在对话中切换知识库。
- Embedding / LLM 走 OpenAI 兼容协议，配置由 Nacos 注入；langfuse 做 trace 观测。
- 对话与知识库全程租户隔离，密钥不外泄。

**Non-Goals:**
- 不实现跨租户共享知识库。
- 不做 OCR / 复杂版面解析（一期仅纯文本文件，二进制文件 minio 下载失败则标记跳过）。
- 不做多模态向量（图像 embedding 等留三期）。
- 不实现知识库的增量更新与版本管理（重新向量化即重建分片）。
- 不替换一期已稳定的文件上传/存储链路。

## Decisions

### 1. 向量化触发模型：文件夹 → 知识库 1:1，异步 MQ 编排
绑定一个文件夹对应一个 `knowledge_base` 记录（`knowledge_base.file_id` 指向文件夹 file_meta）。点击"向量化"按钮 → core-service 创建/复用 KB 记录（status=processing）→ 发 `kb.index.request` MQ 消息 → 消费者拉取 MinIO 文件、分片、调 `EmbeddingProvider.embedBatch`、写 `kb_chunk` + HNSW 索引 → 更新 KB status=ready。
**理由**：复用一期预留 MQ 通道，解耦前端与重任务，天然幂等（按 kb_id + file_id 去重）。**备选**：同步 HTTP 触发 + 轮询——被否，大文件会阻塞请求线程。

### 2. 文件大小提示策略：按总大小分档提示，分片大小自适应
按文件夹总大小：≤50MB 正常；50MB–500MB 提示"文件较多，分片较大可能影响精度"；>500MB 强提示"精度可能下降，建议拆分"。分片：默认按 512 tokens 切（文本约 1500 字），超大文件（>20MB）动态放大到 1024 tokens 减少片数，记录在 `kb_chunk.metadata`。
**理由**：用户明确需要"过大提示"与"大文件大分片"。**备选**：固定分片——被否，超大文件片数爆炸。

### 3. LLM / Embedding 访问层：`common` 新增 `LlmClient` + `OpenAICompatibleEmbeddingProvider`
`common` 新增 `LlmProperties`（`@ConfigurationProperties("llm")`：provider、base-url、api-key、chat-model、embedding-model、embedding-dimension）与 `langfuse` 配置。`OpenAICompatibleEmbeddingProvider implements EmbeddingProvider`（`@ConditionalOnProperty` 启用），用 WebClient 调 `{base-url}/embeddings`，1536 维。对话用 `LlmClient.chat(...)` 调 `{base-url}/chat/completions`（stream 可选，一期非流式）。
**理由**：OpenAI 兼容协议可适配通义/智谱/OpenRouter 等，最大化灵活性。**备选**：绑定单一厂商 SDK——被否，vendor-lock。

### 4. 对话编排：langgraph4j 图（retrieve → rerank → generate → cite）
采用 `io.github.bscodes:langgraph4j`（Java 移植）构建 `StateGraph`：节点 `retrieve`（pgvector 余弦检索 top-k）→ `rerank`（按分数截断，可选 MMR 去 redundancy）→ `generate`（拼 prompt 调 LLM）→ `cite`（附引用 file/chunk）。会话上下文存 Redis（key=sessionId，TTL 1h，存最近 10 轮）。
**理由**：用户明确要求 langgraph；Java 侧用 langgraph4j 与技术栈一致。**备选**：手写顺序调用——被否，无可观测图编排。

### 5. pgvector 检索：HNSW 余弦索引 + 自定义 `VectorTypeHandler`
`init-db.sql` 增 `CREATE INDEX ... USING hnsw (embedding vector_cosine_ops)`。`common` 新增 `VectorTypeHandler implements TypeHandler<float[]>`（参考已有 `JsonbTypeHandler`），在 JDBC 层把 `float[]`↔`"[0.1,0.2,...]"` 文本互转。检索 SQL 用 XML mapper 自定义：`SELECT ... FROM kb_chunk WHERE kb_id=? ORDER BY embedding <=> CAST(? AS vector) LIMIT k`（`<=>` 余弦距离）。
**理由**：`<=>` 走 HNSW 索引高性能；TypeHandler 对齐 MyBatis-Plus 风格。**备选**：用 `pgvector` Java driver 依赖——被否，多一层依赖、版本兼容风险。

### 6. Langfuse 观测：docker-compose 部署 + Java SDK 手动 span
`docker-compose.yml` 加 `langfuse` 服务（复用现有 Postgres 建独立库 `langfuse`，web 端口 3000）。Java 侧用 langfuse Java SDK 在 langgraph 每个节点包 `langfuse.trace()/span()`，记录 retrieve 命中、llm token 用量。
**理由**：用户明确要求 langfuse；复用 PG 减容器。**备选**：用 SkyWalking 已有链路——被否，用户指定 langfuse。

### 7. 对话接入前置校验：KB status=ready
前端"进入 RAG 对话"按钮仅当该文件夹对应 KB `status=ready` 时启用；后端 `/api/rag/chat` 收到 `kbId` 后再次校验 status=ready 且 `tenant_id` 匹配，否则返回 `KB_NOT_READY`。
**理由**：用户要求"所有文件准备好后才可对话"。后端兜底防绕过。

## Risks / Trade-offs

- **[pgvector HNSW 索引构建慢]** → 索引在向量化写完后由 DB 异步构建；超大 KB（>10 万片）检索延迟可能 >100ms，一期接受，三期可换 IVFFlat 或调 `hnsw.ef_search`。
- **[大文件分片精度下降]** → 已在 UI 提示 + metadata 记录分片策略；非文本/二进制文件向量化失败则 `kb_chunk.embedding` 留空、status 标 `skipped`，不阻塞整库 ready。
- **[langgraph4j 成熟度]** → 仓库活跃度中等；风险：API 变更。缓解：锁定具体版本，必要时可降级为手写状态机（图结构简单 4 节点）。
- **[langfuse 复用 PG 端口冲突]** → langfuse 需建独立 DB `langfuse`，与业务库共存于同实例；迁移脚本需在 init-db.sql 加 `CREATE DATABASE langfuse`。
- **[LLM 密钥泄露风险]** → `api-key` 用 `${LLM_API_KEY}` 占位，实际值走 Nacos 加密配置或环境变量；日志对 `Authorization` 头脱敏。
- **[MQ 向量化任务幂等]** → 消息含 `kbId`+`attemptId`，消费前以 Redis `SETNX kb:lock:{kbId}` 抢锁，避免重复全量向量化；重复消费直接 ACK。

## Migration Plan

1. **基础设施**：`docker-compose.yml` 加 langfuse；`init-db.sql` 加 HNSW 索引 + `CREATE DATABASE langfuse`；Nacos 配置加 `llm`/`langfuse` 块。
2. **依赖**：`common/pom.xml` 加 langfuse SDK、WebFlux（已有则免）；`core-service/pom.xml` 加 langgraph4j。
3. **后端**：先 `common`（配置 + LlmClient + EmbeddingProvider 实现 + VectorTypeHandler）→ core-service `kb` 模块 → core-service `rag` 模块 → MQ 消费者 → 网关路由。
4. **前端**：进度看板页 → RAG 对话页 → FileListPage 按钮 → 菜单/路由接入。
5. **回滚**：均为新增能力，回滚即移除新模块 + 关路由；DB 索引保留无副作用；langfuse 容器可单独下线。