## ADDED Requirements

### Requirement: 创建分享链接
用户 SHALL 可为文件或文件夹创建分享链接，可选设置提取码、过期时间、最大下载次数。未设置提取码时 MUST 默认生成 6 位随机码。链接 code MUST 不可猜测（≥ 32 位随机串）。

#### Scenario: 创建带提取码的分享
- **WHEN** 用户对 file_id 创建分享，设置 expire_at=2026-12-31, max_downloads=10
- **THEN** 创建 share_link 记录（code 随机、password=BCrypt(提取码)、expire_at、max_downloads），返回分享 URL

#### Scenario: 默认提取码
- **WHEN** 用户创建分享未提供提取码
- **THEN** 系统生成 6 位提取码并在响应中返回一次（不落日志明文）

### Requirement: 访问分享
访客 SHALL 通过分享 code + 提取码访问。系统 MUST 校验：未过期、下载次数未达上限、提取码正确。校验通过后返回预签名下载 URL（文件）或目录树快照（文件夹）。

#### Scenario: 正确提取码下载
- **WHEN** 访客提交正确提取码访问有效分享
- **THEN** 返回预签名下载 URL，share_link.download_count+1

#### Scenario: 提取码错误限流
- **WHEN** 同一分享 10 分钟内提取码错误 5 次
- **THEN** 该分享访问锁定 30 分钟，返回 429（错误码 SHARE_LOCKED）

#### Scenario: 分享过期
- **WHEN** 访客访问已过期的分享
- **THEN** 返回 410 Gone（错误码 SHARE_EXPIRED），不递增下载计数

#### Scenario: 超过下载次数
- **WHEN** download_count 已达 max_downloads
- **THEN** 返回 410 Gone（错误码 SHARE_EXHAUSTED）

### Requirement: 分享管理
分享所有者 SHALL 可查看自己创建的分享列表、取消分享（删除 share_link）、更新提取码/过期/次数。取消分享 MUST 立即失效所有已发出的 URL（预签名 URL 仍可能在 TTL 内有效，但提取码校验失败）。

#### Scenario: 取消分享
- **WHEN** 所有者取消分享 link_id
- **THEN** share_link 删除，后续访客提交提取码返回 410（SHARE_EXPIRED）

#### Scenario: 列出我的分享
- **WHEN** 用户查询自己创建的分享
- **THEN** 返回当前租户内该用户的所有分享记录（分页），含剩余下载次数与过期状态
