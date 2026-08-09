## Purpose
前端应用：工程脚手架、主题切换、文件浏览器、上传组件、路由守卫与错误边界。

## Requirements

### Requirement: 工程脚手架
前端 SHALL 使用 Vite + React + TypeScript + Antd + Tailwind + Zustand + i18next + react-router。项目独立目录，与后端解耦。

#### Scenario: 启动开发
- **WHEN** 执行 `npm run dev`
- **THEN** Vite 开发服务器启动，默认端口 5173，代理 `/api` 到后端网关

### Requirement: 亮/暗主题
前端 SHALL 支持亮/暗主题切换，基于 Antd ConfigProvider + CSS 变量 + Tailwind dark 模式。主题选择持久化到 localStorage，已登录同步到用户偏好。

#### Scenario: 切换暗主题
- **WHEN** 用户点击主题切换按钮切到 dark
- **THEN** 全局 UI 立即切换为暗色，localStorage 存 theme=dark，已登录则同步偏好

#### Scenario: 首次按系统偏好
- **WHEN** 首次访问且 localStorage 无 theme
- **THEN** 按 `prefers-color-scheme` 初始化

### Requirement: 文件浏览器
前端 SHALL 提供文件浏览器：面包屑导航、列表/网格视图、虚拟滚动（react-virtual）支持万级节点、右键菜单（重命名/移动/删除/分享/下载）。所有操作 MUST 反映当前租户上下文。

#### Scenario: 渲染大目录
- **WHEN** 当前目录含 10000 个节点
- **THEN** 列表仅渲染可视区域节点，滚动流畅无卡顿

#### Scenario: 右键菜单操作
- **WHEN** 用户右键文件选择"分享"
- **THEN** 弹出分享创建弹窗，预填 file_id

### Requirement: 上传组件
前端 SHALL 提供自研上传组件：拖拽 + 选择文件、计算 SHA-256（crypto.subtle）调秒传接口、命中则跳过上传、未命中走分片上传（并发分片、断点续传、实时进度、失败重试）。

#### Scenario: 秒传命中
- **WHEN** 用户拖入文件，计算 hash 后后端返回 201（秒传）
- **THEN** 上传列表该文件立即标记完成，不发起任何字节上传

#### Scenario: 分片上传带进度
- **WHEN** 文件未命中秒传，进入分片上传
- **THEN** 显示总进度条与各分片状态，失败分片自动重试 3 次

#### Scenario: 断点续传
- **WHEN** 上传中断后重新打开浏览器继续
- **THEN** 组件用 uploadId 查询已传分片，仅续传缺失分片

### Requirement: 路由守卫与错误边界
前端 SHALL 对受保护路由设守卫（未登录跳登录页），全局错误边界捕获渲染异常并回退到错误页，HTTP 错误按统一错误码 i18n 渲染 toast/message。

#### Scenario: 未登录访问受保护页
- **WHEN** 未登录用户访问 `/files`
- **THEN** 重定向到 `/login?redirect=/files`

#### Scenario: 接口错误提示
- **WHEN** 接口返回 QUOTA_EXCEEDED
- **THEN** 前端按当前语言显示对应错误消息的 toast
