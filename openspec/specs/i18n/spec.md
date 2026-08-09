## Purpose
国际化能力：后端多语言解析、错误码 i18n、前端语言联动。

## Requirements

### Requirement: 后端多语言解析
后端 SHALL 通过 `Accept-Language` 请求头解析 locale（支持 zh、en，默认 zh）。`LocaleResolver` MUST 按优先级确定 locale：用户登录态偏好 > `Accept-Language` 头 > 默认 zh。

#### Scenario: 未登录按请求头
- **WHEN** 未登录请求携带 `Accept-Language: en`
- **THEN** 错误消息按 en 解析

#### Scenario: 登录后按偏好
- **WHEN** 已登录用户偏好 locale=zh，请求头 `Accept-Language: en`
- **THEN** 按 zh 解析（偏好优先）

#### Scenario: 不支持的语言回退
- **WHEN** 请求 `Accept-Language: fr`
- **THEN** 回退到默认 zh

### Requirement: 错误码 i18n
所有业务错误响应 SHALL 返回统一结构 `{code, message, args}`，`message` 由 `MessageSource` 按 locale 解析，`args` 用于占位符填充。message 文件位于 `messages_zh.properties` / `messages_en.properties`。

#### Scenario: 中文错误
- **WHEN** locale=zh 触发 QUOTA_EXCEEDED
- **THEN** message="配额已超限"

#### Scenario: 英文错误
- **WHEN** locale=en 触发 QUOTA_EXCEEDED
- **THEN** message="Quota exceeded"

#### Scenario: 带参数错误
- **WHEN** 触发 NAME_CONFLICT 且 locale=en
- **THEN** message="Name '{name}' already exists", args={name: "a.txt"}

### Requirement: 前端语言联动
前端 SHALL 通过 i18next 管理中/英资源，请求后端时设置 `Accept-Language` 头与当前语言一致。用户切换语言 MUST 持久化到 localStorage 并同步到用户偏好（已登录时）。

#### Scenario: 切换语言
- **WHEN** 用户在前端切换到 English
- **THEN** UI 立即切换为英文，localStorage 存 lang=en，已登录则调用 `PUT /api/user/preferences` 同步

#### Scenario: 初次访问按浏览器
- **WHEN** 首次访问且 localStorage 无 lang
- **THEN** 按 `navigator.language` 初始化（zh-* → zh，其余 → en）
