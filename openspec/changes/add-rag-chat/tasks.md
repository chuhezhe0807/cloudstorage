## 1. 基础设施与配置

- [x] 1.1 `docker-compose.yml` 新增 langfuse 服务（复用 pgvector Postgres，建 `langfuse` 库；web 端口 3000；环境变量接 public-key/secret-key），与现有服务同级
- [x] 1.2 `scripts/init-db.sql` 为 `kb_chunk.embedding` 增 `CREATE INDEX ... USING hnsw (embedding vector_cosine_ops)` 索引；新增 `CREATE DATABASE langfuse`（或 langfuse 自行初始化脚本挂载）
- [x] 1.3 `docs/nacos-cloudstorage-common.yaml` 新增 `llm`（provider/base-url/api-key/chat-model/embedding-model/embedding-dimension）与 `langfuse`（base-url/public-key/secret-key）配置块
- [x] 1.4 各服务 `application.yml` 确认 `spring.config.import` 已引入 cloudstorage-common.yaml；设置 `LLM_API_KEY`/`LANGFUSE_*` 环境变量占位

## 2. common 模块：LLM / Embedding / VectorType 配置层

- [x] 2.1 新增 `LlmProperties`（`@ConfigurationProperties("llm")`）与 `LangfuseProperties`（`@ConfigurationProperties("langfuse")`），字段与 Nacos 配置块对应
- [x] 2.2 实现 `OpenAICompatibleEmbeddingProvider implements EmbeddingProvider`（`@ConditionalOnProperty`），用 WebClient POST `{base-url}/embeddings` 返回 1536 维；`embedBatch` 单次批量 ≤ 100 条
- [x] 2.3 新增 `LlmClient`（WebClient 调 `{base-url}/chat/completions`，非流式，支持 messages 数组、model、temperature），日志对 Authorization/api-key 脱敏
- [x] 2.4 新增 `VectorTypeHandler implements TypeHandler<float[]>`，`float[]` ↔ pgvector 文本格式 `"[0.1,0.2,...]"` 互转；在 `CommonAutoConfiguration` 注册
- [x] 2.5 langfuse Java SDK 接入封装 `LangfuseTraceHelper`（trace/span 上报，不可用时降级仅 WARN 日志）
- [x] 2.6 `pom.xml` 新增依赖（langfuse SDK、WebFlux 若无、langgraph4j 留 core-service）
- [x] 2.7 新增错误码 `KB_NOT_READY`、`KB_NOT_FOUND`、`UNSUPPORTED_FILE_TYPE`、`VECTORIZE_FAILED`、`LLM_CALL_FAILED` 到 `ErrorCode` 枚举，补 i18n message（中/英）

## 3. core-service：kb 模块（知识库 + 向量化）

- [x] 3.1 建包 `com.chuhezhe.core.kb` 下 `controller/service/entity/mapper/dto`（沿用 file/storage 模块约定）
- [x] 3.2 `KnowledgeBase` 实体（extends BaseEntity，字段：tenantId、ownerId、fileId[文件夹]、name、status[processing/ready/partial/failed]、totalFiles、processedFiles）、`KbChunk` 实体（tenantId、kbId、fileId、chunkIndex、content、embedding[float[]+VectorTypeHandler]、metadata[JSONB]）
- [x] 3.3 `KnowledgeBaseMapper`、`KbChunkMapper`（BaseMapper）；复杂检索用 XML mapper：`SELECT ... FROM kb_chunk WHERE kb_id=? ORDER BY embedding <=> CAST(#{query} AS vector) LIMIT #{k}`
- [x] 3.4 `KbService`：`triggerVectorize(fileId)` —— 校验是文件夹、查询/复用 KB 记录（按 tenantId+fileId）、置 status=processing、发 `kb.index.request` MQ 消息（含 kbId+tenantId）；`getProgress(tenantId)` 返回 KB 进度列表含 failedFiles
- [x] 3.5 `KbController`（`@RequestMapping("/api/kb")`）：`POST /index`（触发）、`GET /progress`（看板列表）、`GET /progress/{kbId}`（单 KB 含 failedFiles 明细）
- [x] 3.6 MQ 生产端：复用 `RabbitMqConstants.KB_INDEX_*` 发消息（已有 eventExchange）；消息体含 kbId/tenantId/attemptId 去重字段

## 4. core-service：向量化消费者

- [x] 4.1 core-service 新增 RabbitMQ 配置（监听 `KB_INDEX_QUEUE`，之前仅 notification 声明），`@RabbitListener` 消费 `kb.index.request`
- [x] 4.2 `KbIndexConsumer`：收消息 → `SETNX kb:lock:{kbId}` 抢锁 → 拉取该文件夹下所有非删除 file_meta → 遍历 → 从 MinIO 下载文件内容
- [x] 4.3 文件解析分片：文本文件按 512 tokens 切（>20MB 动态 1024 tokens）；二进制/不可解析标记 failed（UNSUPPORTED_FILE_TYPE），不阻塞
- [x] 4.4 调 `EmbeddingProvider.embedBatch(chunks)` 批量向量化 → 写 `kb_chunk`（embedding 非空、metadata 记录分片策略）→ `knowledge_base.processed_files` 累加
- [x] 4.5 全部处理完更新 status=ready；部分失败 status=partial 并写 failed_files（文件名+原因）；整体异常 status=failed
- [x] 4.6 幂等：重复消息（锁已占或 status=ready）直接 ACK 不重复执行
- [x] 4.7 经 langfuse 记录向量化 trace（每个文件一个 span，含片数/token 用量）

## 5. core-service：rag 模块（langgraph 对话）

- [x] 5.1 建包 `com.chuhezhe.core.rag` 下 `controller/service/graph/dto`
- [x] 5.2 引入 langgraph4j 依赖，构建 `StateGraph`：节点 `retrieve`（调 KbChunkMapper 检索 top-k=5）→ `rerank`（余弦分数 <0.3 过滤，top-3 截断 + MMR 去冗余）→ `generate`（拼 prompt 调 LlmClient）→ `cite`（附引用 file_name + chunk 片段）
- [x] 5.3 `RagService.chat(sessionId, kbId, message)`：校验 KB status=ready/partial + tenant 一致 → 取 Redis 会话上下文（最近 10 轮）→ 跑 graph → 写回上下文 → 返回 answer+citations
- [x] 5.4 会话上下文存 Redis（key=`rag:session:{sessionId}`, TTL=1h）；sessionId 首次生成
- [x] 5.5 `RagController`（`@RequestMapping("/api/rag")`）：`POST /chat`（body: kbId/message/sessionId?）→ 返回 answer+citations+sessionId；`GET /kbs` 返回当前租户 ready/partial KB 列表供前端切换
- [x] 5.6 langfuse trace 上报：对话级 trace，retrieve（命中数/耗时）、generate（token 用量）span；不可用降级
- [x] 5.7 错误处理：KB_NOT_READY → 409；跨租户 kbId → 404；LLM 调用失败 → 503 LLM_CALL_FAILED

## 6. 网关路由

- [x] 6.1 `gateway-service` 路由配置（Nacos application.yml 或本地）core-service predicate 增加 Path 匹配 `/api/kb/**`、`/api/rag/**`
- [x] 6.2 确认 `/api/kb/**`、`/api/rag/**` 非白名单（需 JWT 鉴权），网关集成测试验证转发 + 租户头注入

## 7. 前端：向量化进度看板页

- [x] 7.1 新增 `frontend/src/pages/kb/KbProgressPage.tsx`：表格列出当前租户 KB（文件夹名、总文件数、已处理数、状态、进度条 = processed/total），5s 轮询
- [x] 7.2 partial/failed 行支持点击展开 failed_files 明细（文件名 + 失败原因）
- [x] 7.3 调用 `apiClient.get('/kb/progress')` / `apiClient.get('/kb/progress/{kbId}')`；i18n 文案中/英

## 8. 前端：RAG 对话页

- [x] 8.1 新增 `frontend/src/pages/rag/RagChatPage.tsx`：顶部 KB 切换下拉（GET `/rag/kbs` 取 ready/partial），下方气泡式对话区 + 输入框
- [x] 8.2 发送消息 POST `/rag/chat`（kbId/message/sessionId），展示用户气泡与 AI 回答气泡；回答下方列引用来源（file_name + 片段预览，可点跳转 `/files`）
- [x] 8.3 切换 KB 时清空检索上下文（保留对话文字历史），前端传新 kbId 续 sessionId
- [x] 8.4 新增 `frontend/src/store/ragStore.ts`（Zustand persist：sessionId/kbId/消息历史）
- [x] 8.5 i18n 文案中/英；加载/错误态；KB_NOT_READY 等错误码 toast

## 9. 前端：FileListPage 按钮接入 + 菜单/路由

- [x] 9.1 `FileListPage.tsx` 工具栏（`flex gap-2` div）新增"向量化"按钮：点击调 `/files` 算当前文件夹总大小，按档弹窗提示（≤50MB 无提示直接发；50–500MB 提示精度；>500MB 强提示），确认后 POST `/kb/index`，toast Success 且提示可前往看板
- [x] 9.2 `FileListPage.tsx` 工具栏新增"进入 RAG 对话"按钮：查询当前文件夹对应 KB 状态（或全量 progress 比对），status=ready/partial 启用跳转 `/rag/chat?kbId=K`，否则置灰 tooltip 提示向量化未完成
- [x] 9.3 `MainLayout.tsx` `menuItems` 新增两项：`{ key:'/kb/progress', icon, label: t('nav.kbProgress') }`、`{ key:'/rag/chat', icon, label: t('nav.ragChat') }` + 图标 import + i18n key
- [x] 9.4 `router/index.tsx` 在 `'/'` layout children 新增 `{ path:'kb/progress', element:<KbProgressPage/> }`、`{ path:'rag/chat', element:<RagChatPage/> }` + import
- [x] 9.5 文件大小档位计算与提示文案抽到 util；i18n key 补 zh/en

## 10. 测试与质量门禁

- [x] 10.1 kb 模块单元测试（Mockito）：triggerVectorize 复用 KB / 进度更新 / status 流转；消费者幂等 / 二进制跳过 / 部分 partial
- [x] 10.2 rag 模块单元测试：graph 编排 / 无相关分片不调 LLM / KB_NOT_READY / 跨租户 404
- [x] 10.3 common 单元测试：LlmClient 脱敏 / VectorTypeHandler 互转 / EmbeddingProvider 批量
- [x] 10.4 接口测试（RestAssured + Testcontainers）：POST /api/kb/index → 轮询 /api/kb/progress → ready → POST /api/rag/chat 返回 answer+citations
- [x] 10.5 前端组件测试 / Playwright E2E：FileListPage 向量化按钮档位提示、看板轮询、对话发送与引用展示、切换 KB
- [x] 10.6 JaCoCo 覆盖率复核 ≥ 70%；`mvn clean verify` 通过