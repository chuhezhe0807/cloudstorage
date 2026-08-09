## Purpose
文件元数据与目录树管理：目录树模型、CRUD 操作、回收站、文件搜索。

## Requirements

### Requirement: 目录树模型
文件元数据 SHALL 同时维护 `parent_id` 与物化路径 `path`（如 `/docs/work/`）。移动节点 MUST 原子更新子树所有节点的物化路径前缀。并发移动同一目录 MUST 通过 Redis 分布式锁串行化。

#### Scenario: 创建目录
- **WHEN** 用户在 `/docs/` 下创建子目录 `work`
- **THEN** 创建 file_meta（is_dir=true, parent_id=父目录, path=/docs/work/），返回 201

#### Scenario: 移动子树
- **WHEN** 用户将 `/docs/work/` 移动到 `/archive/`
- **THEN** 该目录及其所有后代节点的 path 前缀从 `/docs/work/` 更新为 `/archive/work/`，parent_id 同步更新

#### Scenario: 并发移动加锁
- **WHEN** 两个请求同时移动 `/docs/work/`
- **THEN** 后到的请求等待 Redis 锁，锁超时（5s）后返回 409（错误码 CONCURRENT_MOVE）

### Requirement: 文件元数据 CRUD
系统 SHALL 提供文件/目录的创建、查询、重命名、移动、删除（软删除入回收站）、复制元数据（不复制字节）接口。所有操作 MUST 带 `tenant_id` 过滤。

#### Scenario: 重命名
- **WHEN** 用户将 `a.txt` 重命名为 `b.txt`（同目录）
- **THEN** 仅更新 name 与 path，size/hash 不变，返回 200

#### Scenario: 同目录同名冲突
- **WHEN** 重命名导致目标目录下已存在同名节点
- **THEN** 返回 409（错误码 NAME_CONFLICT）

### Requirement: 回收站
删除文件/目录 SHALL 软删除（置 `deleted_at`），进入回收站。回收站支持还原（清空 `deleted_at`）与永久删除（物理删除对象、释放配额、扣 ref_count）。回收站保留期默认 30 天，到期自动永久删除。

#### Scenario: 软删除进回收站
- **WHEN** 用户删除文件 `a.txt`
- **THEN** file_meta.deleted_at 置当前时间，文件列表默认查询不返回该节点，回收站列表返回

#### Scenario: 还原
- **WHEN** 用户从回收站还原 `a.txt`，且原父目录仍存在且无同名冲突
- **THEN** deleted_at 清空，文件回到原位置

#### Scenario: 永久删除释放配额
- **WHEN** 用户永久删除一个引用计数为 1 的 100MB 文件
- **THEN** MinIO 对象删除、file_content.ref_count 减至 0 并删除、file_meta 删除、租户配额释放 100MB

### Requirement: 文件搜索
系统 SHALL 提供按名称模糊搜索（当前租户内），支持按类型/大小/时间过滤与排序。搜索结果 MUST 分页。

#### Scenario: 名称搜索
- **WHEN** 用户搜索关键词 `report`
- **THEN** 返回当前租户内 name 含 `report` 的节点列表（分页），不包含回收站项

#### Scenario: 多条件过滤
- **WHEN** 用户筛选 type=file, size>=10MB, created_after=2026-01-01
- **THEN** 仅返回符合所有条件的节点
