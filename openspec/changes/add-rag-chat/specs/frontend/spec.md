## MODIFIED Requirements

### Requirement: 文件浏览器
前端 SHALL 提供文件浏览器：面包屑导航、列表/网格视图、虚拟滚动（react-virtual）支持万级节点、右键菜单（重命名/移动/删除/分享/下载）。所有操作 MUST 反映当前租户上下文。进入某文件夹后，工具栏 SHALL 显示"向量化"按钮（触发该文件夹向量化任务，点击前按文件夹总大小给出提示）与"进入 RAG 对话"按钮（仅当该文件夹对应知识库 status 为 ready 或 partial 时启用，点击跳转 `/rag/chat?kbId=...`）。

#### Scenario: 渲染大目录
- **WHEN** 当前目录含 10000 个节点
- **THEN** 列表仅渲染可视区域节点，滚动流畅无卡顿

#### Scenario: 右键菜单操作
- **WHEN** 用户右键文件选择"分享"
- **THEN** 弹出分享创建弹窗，预填 file_id

#### Scenario: 触发向量化
- **WHEN** 用户进入文件夹 F 并点击"向量化"按钮
- **THEN** 按文件夹总大小弹窗提示（≤50MB 无提示直接发请求；50–500MB 提示精度；>500MB 强提示建议拆分），用户确认后 POST `/api/kb/index`（fileId=F），成功后 toast 并可前往向量化进度看板查看

#### Scenario: 进入 RAG 对话
- **WHEN** 文件夹 F 对应 KB status=ready，用户点击"进入 RAG 对话"按钮
- **THEN** 跳转到 `/rag/chat?kbId=K`，对话页以 K 为当前知识库初始化

#### Scenario: RAG 按钮门控
- **WHEN** 文件夹 F 对应 KB status=processing 或无 KB 记录
- **THEN** "进入 RAG 对话"按钮置灰，tooltip 提示向量化未完成，不可点击

## ADDED Requirements

### Requirement: 向量化进度看板页
前端 SHALL 提供向量化进度看板页（路由 `/kb/progress`，左侧一级菜单入口）。看板 SHALL 展示当前租户所有知识库的向量化进度（文件夹名、总文件数、已处理数、状态、失败文件明细），轮询刷新（5s 间隔），并支持从 FileListPage 跳入。

#### Scenario: 查看向量化进度
- **WHEN** 用户点击左侧菜单"向量化进度"
- **THEN** 右侧展示看板，列表每行展示一个 KB 的进度（进度条 = processed/total），processing 行 5s 轮询更新

#### Scenario: 查看失败明细
- **WHEN** 用户点击某 partial/failed KB 行的"失败明细"
- **THEN** 展开该 KB 的 failed_files（文件名 + 失败原因）

### Requirement: RAG 对话页
前端 SHALL 提供 RAG 对话页（路由 `/rag/chat`，左侧一级菜单入口）。对话页 SHALL 支持多轮对话展示（气泡式）、在界面上方切换当前 RAG 知识库（下拉选当前租户已 ready 的 KB）、展示回答附带的引用来源（可点击跳转文件）。

#### Scenario: 发起对话
- **WHEN** 用户在 RAG 对话页选择 KB=K 并发送消息"总结一下"
- **THEN** 前端 POST `/api/rag/chat`（kbId=K, message），展示用户气泡与 AI 回答气泡

#### Scenario: 展示引用
- **WHEN** AI 回答附带 3 条引用
- **THEN** 回答气泡下方列出引用来源（file_name + 片段预览），点击可跳转到对应文件

#### Scenario: 切换知识库
- **WHEN** 用户在对话页顶部将 KB 从 K1 切换为 K2
- **THEN** 对话清空基于 K1 的检索上下文，后续消息以 K2 检索，对话文字历史可保留

### Requirement: 左侧菜单新增入口
前端 MainLayout 左侧一级菜单 SHALL 新增两个入口："向量化进度"（key `/kb/progress`）与"RAG 对话"（key `/rag/chat`），与现有 Files/Recycle/Shares/Notifications 同级。

#### Scenario: 菜单展示
- **WHEN** 已登录用户查看左侧菜单
- **THEN** 菜单含向量化进度与 RAG 对话两项，点击分别导航到 `/kb/progress` 与 `/rag/chat`

#### Scenario: 路由守卫
- **WHEN** 未登录用户访问 `/kb/progress` 或 `/rag/chat`
- **THEN** AuthGuard 重定向到登录页（与其它受保护路由一致）