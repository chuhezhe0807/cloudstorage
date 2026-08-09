## ADDED Requirements

### Requirement: 知识库绑定文件夹创建
系统 SHALL 支持以文件夹为粒度创建知识库：一个文件夹对应一个 `knowledge_base` 记录（`file_id` 指向该文件夹 file_meta）。同一文件夹重复触发向量化 SHALL 复用已存在的 KB 记录，不重复创建。所有 KB 记录 MUST 带 `tenant_id` 行级隔离。

#### Scenario: 首次向量化
- **WHEN** 用户对文件夹 F（租户 T1）点击向量化按钮，且 T1 内无 F 关联的 KB 记录
- **THEN** 创建 knowledge_base（tenant_id=T1, file_id=F, status=processing），发 kb.index.request 消息，返回 202 与 kbId

#### Scenario: 重复触发复用
- **WHEN** 文件夹 F 已有 KB 记录且 status=processing 或 ready
- **THEN** 不重复创建 KB 记录，返回已有 kbId（processing 时提示进行中）

#### Scenario: 跨租户隔离
- **WHEN** 用户 A（租户 T1）查询 KB 列表
- **THEN** 仅返回 T1 的 KB 记录，不返回租户 T2 的 KB

### Requirement: 向量化进度跟踪
系统 SHALL 持久化向量化进度（`knowledge_base.total_files`、`processed_files`、`status`、`failed_files`）。状态包括 `processing`、`ready`、`partial`、`failed`。前端进度看板 SHALL 按 KB 维度展示总文件数、已处理数、状态、失败文件明细，并支持轮询刷新。

#### Scenario: 进度看板查询
- **WHEN** 用户 GET `/api/kb/progress`（当前租户）
- **THEN** 返回该租户所有 KB 的进度列表（kbId、文件夹名、总文件数、已处理数、status、failedFiles）

#### Scenario: 处理中
- **WHEN** 某 KB 正在分片 + 向量化，已完成 3/10 个文件
- **THEN** status=processing，processed_files=3，total_files=10，看板显示进度 30%

#### Scenario: 全部成功
- **WHEN** KB 下所有文件向量化成功
- **THEN** status=ready，processed_files=total_files，前端"进入 RAG 对话"按钮对该文件夹启用

#### Scenario: 部分失败
- **WHEN** KB 下 10 个文件中 2 个向量化失败（如二进制文件无法解析）
- **THEN** status=partial，failed_files 含失败文件名与原因，其余 8 个仍可参与对话

### Requirement: 异步分片与向量化
系统 SHALL 异步消费 `kb.index.request` 消息执行分片与向量化：从 MinIO 下载文件 → 文本分片（默认 512 tokens，>20MB 文件动态放大到 1024 tokens）→ 调 `EmbeddingProvider.embedBatch` 生成 1536 维向量 → 写 `kb_chunk`（含 embedding）。消费 MUST 幂等（按 kbId 锁定，重复消息 ACK 不重复执行）。

#### Scenario: 文本文件分片向量化
- **WHEN** 消费者收到 kbId 消息，对应 3 个纯文本文件
- **THEN** 逐文件下载、分片、embedBatch、写 kb_chunk（embedding 非空），processed_files 累加，最终 status=ready

#### Scenario: 二进制文件跳过
- **WHEN** 某文件为不可解析的二进制（如 .exe）
- **THEN** 该文件标记失败（原因=UNSUPPORTED_TYPE），processed_files 不增，failed_files 累加，继续处理其余文件

#### Scenario: 重复消费幂等
- **WHEN** 同一 kbId 消息被消费两次
- **THEN** 第一次以 `SETNX kb:lock:{kbId}` 抢锁执行，第二次发现锁/已 ready 直接 ACK 不重复向量化

### Requirement: 文件大小提示
前端触发向量化前 SHALL 按文件夹总大小给出提示：≤50MB 正常进行；50MB–500MB 提示"文件较多，分片较大可能影响精度"；>500MB 强提示"精度可能下降，建议拆分后再向量化"。提示 MUST 经用户确认后才发向量化请求。

#### Scenario: 小文件夹
- **WHEN** 文件夹总大小 20MB
- **THEN** 直接触发向量化，无额外提示

#### Scenario: 中等文件夹
- **WHEN** 文件夹总大小 200MB
- **THEN** 弹窗提示"文件较多，分片较大可能影响精度"，用户确认后才发请求

#### Scenario: 超大文件夹
- **WHEN** 文件夹总大小 800MB
- **THEN** 强提示"精度可能下降，建议拆分后再向量化"，用户确认后才发请求

### Requirement: 网关路由
网关 SHALL 将 `/api/kb/**` 路由到 core-service，并纳入 JWT 鉴权（非白名单）。

#### Scenario: 访问向量化接口
- **WHEN** 已登录用户携带有效 JWT 访问 `/api/kb/progress`
- **THEN** 网关转发到 core-service，注入 X-Tenant-Id