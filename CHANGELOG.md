# Changelog

## Unreleased

### Changes

- 实现 Tool Use / Function Calling：支持 read_file、write_file、list_files、get_datetime、execute_shell 工具
- AgentLoopService 支持工具调用循环（最多 5 轮），非流式 chatComplete 执行后回交流式结果
- 会话持久化：按 sessionKey 保存聊天记录到本地 JSON，后端重启自动加载
- OpenAI 与 Anthropic Provider 支持 tools 参数和 tool_calls / tool_result 解析
- 前端体验优化：错误消息红色高亮、消息重试按钮、代码块复制按钮
- 新增默认供应商配置：DeepSeek、GLM、Kimi、Bailian、MiniMax
- 供应商配置持久化到 `~/.qoderwork/myclaw/providers.json`
- AnthropicProvider SSE 解析增强兼容性，支持国内厂商变体格式
- 前端添加 Sessions、Files、Settings 页面

### Fixes

- 防止 assistant 消息在 Refresh 时重复写入
- 修复 Anthropic 认证头冗余导致的请求被拒问题
- 前端流式错误信息正确显示

## 2025.04

### Highlights

- 最小可用的 MyClaw Gateway 实现：Java + Spring Boot + Vue3
- 兼容 OpenClaw WebSocket 协议
- 支持 OpenAI GPT 流式对话
- WebSocket 自动重连、Markdown 渲染、代码高亮
