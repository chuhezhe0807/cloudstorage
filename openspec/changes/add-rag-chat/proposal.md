## Why

一期 MVP 已预留知识库表（`knowledge_base` / `kb_chunk` + pgvector）、`EmbeddingProvider` SPI 与 `kb.index.request` 队列，但未实现 RAG 能力。用户需要一个基于已上传文件的智能问答入口：将某文件夹下所有文件分片、向量化后，开启基于该文件夹知识库的多轮对话。本变更补全二期 RAG 能力，让云存储从"存"升级到"问"。

## What Changes

- 前端文件列表页文件夹维度新增"向量化"按钮：点击后触发后台任务对该文件夹下所有文件分片 + 上传向量库；按文件夹总大小给出提示（过大时分片更大、agent 精度下降）。
- 前端左侧一级菜单新增"向量化进度"入口，右侧展示分片 + 向量化进度看板（按知识库/文件夹维度，展示总文件数、已处理数、状态、失败详情）。
- 前端文件列表页进入某文件夹后新增"RAG 对话"按钮（仅在该文件夹所有文件向量化完成后可进入）。
- 前端左侧一级菜单新增"RAG 对话"入口，右侧展示对话界面，可在对话界面切换 RAG 知识库（文件夹）。
- 后端 core-service 新增 `kb` 模块：知识库（绑定文件夹）创建、向量化任务编排、分片存储与 pgvector 检索。
- 后端 core-service 新增 `rag` 模块：基于 langgraph 的多轮对话编排（检索 → 重排 → 生成 → 引用）。
- 实现 `EmbeddingProvider`（OpenAI 兼容协议，1536 维），LLM baseurl/apikey 通过 Nacos 配置注入。
- 引入 langfuse（docker-compose 部署）对 agent 链路做 trace / 观测。
- MQ 消费 `kb.index.request` 事件，异步执行分片 + 向量化，进度写库 + 推送更新。
- `scripts/init-db.sql` 为 `kb_chunk.embedding` 增加 HNSW 余弦相似索引。
- 网关路由新增 `/api/kb/**` 与 `/api/rag/**` 转发到 core-service。

## Capabilities

### New Capabilities
- `knowledge-base`: 知识库（绑定文件夹）创建、向量化任务触发、分片与向量化进度跟踪、异步消费执行。
- `rag-chat`: 基于 langgraph 的多轮检索增强对话（检索 → 重排 → 生成 → 引用），对话会话管理与上下文切换知识库。
- `llm-config`: LLM / Embedding / Langfuse 的配置与访问层（Nacos 注入、密钥安全、Langfuse 观测接入）。

### Modified Capabilities
- `frontend`: 文件列表页新增向量化/RAG 对话按钮、左侧菜单新增两个一级入口与对应路由页（向量化进度看板、RAG 对话页）。

## Impact

- **代码**：`core-service` 新增 `kb`、`rag` 两个业务模块（controller/service/entity/mapper/dto）；`common` 新增 LLM/Embedding/Langfuse 配置类与 `EmbeddingProvider` 实现；`common` 新增 pgvector 的 `VectorTypeHandler`；`frontend` 新增两个页面 + 路由 + 菜单项 + store。
- **API**：新增 `/api/kb/**`（知识库与向量化进度）、`/api/rag/**`（对话）；网关路由与白名单需相应调整。
- **依赖**：引入 langgraph4j（或等价 Java langgraph 实现）、langfuse Java SDK、pgvector JDBC 支持、OpenAI 兼容 HTTP 客户端。
- **基础设施**：`docker-compose.yml` 新增 langfuse 服务；`init-db.sql` 增加 HNSW 索引；`docs/nacos-cloudstorage-common.yaml` 新增 `llm` 与 `langfuse` 配置块。
- **MQ**：启用 `kb.index.request` 队列消费者，定义消息格式与幂等约束。
- **安全/租户**：知识库与分片沿用 `tenant_id` 行级隔离；对话仅可访问当前租户知识库；LLM 调用不外泄明文密钥。