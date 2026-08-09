## ADDED Requirements

### Requirement: RAG 对话编排
系统 SHALL 基于 langgraph 编排多轮 RAG 对话，节点顺序为 retrieve（pgvector 余弦检索 top-k）→ rerank（按分数截断 + 去冗余）→ generate（拼 prompt 调 LLM 生成）→ cite（附引用来源）。对话编排 MUST 经 langfuse 记录 trace（每个节点一个 span）。

#### Scenario: 正常问答
- **WHEN** 用户对已 ready 的 KB 提问"文件里讲了什么"
- **THEN** 系统检索 top-k=5 分片，重排截断 top-3，调 LLM 生成回答并附 3 条引用（file_name + chunk 片段）

#### Scenario: 无相关分片
- **WHEN** 检索结果最高余弦相似度 < 0.3 阈值
- **THEN** 系统回答"知识库中未找到相关内容"，不调 LLM 编造，引用列表为空

#### Scenario: 链路观测
- **WHEN** 一次对话完成
- **THEN** langfuse 记录一条 trace，含 retrieve（命中数/耗时）、generate（token 用量）span，可在 langfuse UI 查看

### Requirement: 对话会话管理
系统 SHALL 为每个对话创建会话（sessionId），会话上下文（最近 10 轮）存 Redis（TTL 1h）。前端 RAG 对话页 SHALL 支持在对话界面切换 RAG 知识库（文件夹），切换后用新 KB 重新检索，不继承旧 KB 上下文。

#### Scenario: 首次对话建会话
- **WHEN** 用户在 RAG 对话页选择 KB（kbId=K）并发送第一条消息
- **THEN** 后端创建 sessionId，存 Redis，基于 K 检索生成回答

#### Scenario: 多轮上下文
- **WHEN** 同一 sessionId 连续发送第 2、3 条消息
- **THEN** 每次携带 Redis 中最近 10 轮历史作为对话上下文，LLM 生成连贯回答

#### Scenario: 切换知识库
- **WHEN** 用户在对话界面将 KB 从 K1 切换为 K2
- **THEN** 后续检索基于 K2，K1 的历史分片不再参与，但对话文字历史可保留

#### Scenario: 会话过期
- **WHEN** 会话 Idle 超过 1h
- **THEN** Redis 键过期，后续消息视为新会话，不引用旧上下文

### Requirement: 对话前置校验
后端 `/api/rag/chat` 收到 kbId 后 MUST 校验该 KB `status=ready`（partial 也允许，仅用已成功片）且 `tenant_id` 与当前租户一致，否则返回对应错误。前端"进入 RAG 对话"按钮仅当文件夹对应 KB status=ready 或 partial 时启用。

#### Scenario: KB 未就绪
- **WHEN** 用户对 status=processing 的 KB 发起对话
- **THEN** 后端返回 409（错误码 KB_NOT_READY），不执行检索

#### Scenario: 跨租户拒绝
- **WHEN** 用户 A（租户 T1）用 T2 的 kbId 发起对话
- **THEN** 后端返回 404（不泄露存在性）

#### Scenario: 前端按钮门控
- **WHEN** 文件夹 F 对应 KB status=processing
- **THEN** FileListPage 中"进入 RAG 对话"按钮置灰不可点，tooltip 提示"正在向量化"

### Requirement: 网关路由
网关 SHALL 将 `/api/rag/**` 路由到 core-service，并纳入 JWT 鉴权（非白名单）。

#### Scenario: 访问对话接口
- **WHEN** 已登录用户携带有效 JWT POST `/api/rag/chat`
- **THEN** 网关转发到 core-service，注入 X-Tenant-Id