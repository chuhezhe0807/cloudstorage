## ADDED Requirements

### Requirement: 用户注册
系统 SHALL 提供用户注册接口，接受账号（唯一）、密码、可选语言/主题偏好。密码 MUST 使用 BCrypt 加盐存储，明文禁止落库或入日志。

#### Scenario: 注册成功
- **WHEN** 提交唯一账号与合法密码
- **THEN** 创建 user（含个人租户，见 tenant 能力），返回 JWT access/refresh 令牌，HTTP 201

#### Scenario: 账号重复
- **WHEN** 提交已存在的账号
- **THEN** 返回 409 Conflict（错误码 ACCOUNT_EXISTS），不创建任何记录

### Requirement: 登录与 JWT
系统 SHALL 提供账号密码登录，签发 access token（短期，15min）与 refresh token（长期，7 天）。access token 携带 `userId`、`tenantId`、`locale`。

#### Scenario: 登录成功
- **WHEN** 提交正确账号密码
- **THEN** 返回 access + refresh 令牌，HTTP 200

#### Scenario: 密码错误限流
- **WHEN** 同一账号 5 分钟内密码错误 5 次
- **THEN** 该账号登录接口锁定 15 分钟，返回 429 Too Many Requests（错误码 ACCOUNT_LOCKED）

### Requirement: 令牌刷新与撤销
系统 SHALL 提供 refresh token 换新 access token 接口；refresh token MUST 一次性使用（旋转）。用户登出或修改密码 MUST 将对应 refresh token 加入 Redis 黑名单。

#### Scenario: 刷新成功
- **WHEN** 携带有效且未使用的 refresh token 调用刷新接口
- **THEN** 签发新 access + 新 refresh，旧 refresh 立即失效

#### Scenario: 重复使用 refresh
- **WHEN** 已被旋转失效的 refresh token 再次调用刷新
- **THEN** 返回 401 Unauthorized，并撤销该用户所有 refresh token（疑似盗用）

### Requirement: 网关鉴权
网关 SHALL 拦截除白名单（注册/登录/刷新/分享提取）外的所有请求，校验 JWT 签名与有效期，失败返回 401。

#### Scenario: 无令牌访问受保护接口
- **WHEN** 不带 Authorization 头访问 `/api/files`
- **THEN** 网关返回 401，请求不转发到下游

#### Scenario: 令牌过期
- **WHEN** access token 已过期
- **THEN** 网关返回 401（错误码 TOKEN_EXPIRED），前端用 refresh 自动续期

### Requirement: 用户偏好
user 表 SHALL 存 `locale`（zh/en）与 `theme`（light/dark）偏好。登录后用户偏好 MUST 覆盖请求 `Accept-Language` 头用于 i18n 解析。

#### Scenario: 偏好覆盖请求头
- **WHEN** 用户偏好 locale=en，请求头 `Accept-Language: zh`
- **THEN** 系统按 en 解析错误消息（用户偏好优先）

#### Scenario: 更新偏好
- **WHEN** 用户调用 `PUT /api/user/preferences` 提交 locale=zh, theme=dark
- **THEN** 持久化并返回 200，后续请求按新偏好解析
