# MyClaw — Java 版 OpenClaw Gateway 骨架

这是一个最小可用的 MyClaw Gateway 实现，用 Java + Spring Boot + Vue3 构建，兼容 OpenClaw 的 WebSocket 协议。

## 项目结构

```
myclaw/
├── myclaw-core/        # 领域模型 + Gateway 协议定义
├── myclaw-ai/          # AI Provider（OpenAI）+ Agent Loop 运行时
├── myclaw-server/      # Spring Boot Gateway 服务器（WebSocket + HTTP）
├── myclaw-web/         # Vue3 前端（聊天界面）
├── settings.gradle.kts
└── build.gradle.kts
```

## 快速开始

### 1. 环境要求

- JDK 21+
- Gradle 8.x（或直接用 wrapper）
- Node.js 22+
- OpenAI API Key

### 2. 配置 API Key

```bash
# Windows PowerShell
$env:OPENAI_API_KEY="sk-..."

# Linux/macOS
export OPENAI_API_KEY="sk-..."
```

或者在 `myclaw-server/src/main/resources/application.yml` 中直接填写：

```yaml
myclaw:
  openai:
    api-key: sk-...
```

### 3. 启动后端

```bash
cd E:\workspace\github\myclaw
./gradlew :myclaw-server:bootRun
```

Gateway 将监听 `http://localhost:18789`，WebSocket 端点为 `ws://localhost:18789/ws`。

### 4. 启动前端（开发模式）

```bash
cd myclaw-web
npm install
npm run dev
```

前端将运行在 `http://localhost:5173`，并通过代理连接后端的 WebSocket。

### 5. 打包（前后端一体）

```bash
# 先构建前端（输出到 myclaw-server/src/main/resources/static）
cd myclaw-web
npm install
npm run build

# 再构建可运行的 JAR
cd ..
./gradlew :myclaw-server:bootJar

# 运行
java -jar myclaw-server/build/libs/myclaw-server-0.1.0-SNAPSHOT.jar
```

## 已实现的功能

### Gateway 协议（WebSocket）

| 方法 | 说明 |
|------|------|
| `connect` | 客户端握手，返回可用方法和事件列表 |
| `health` | 健康检查 |
| `agent` | 发送消息给 AI Agent，流式返回 assistant / lifecycle / error 事件 |
| `chat` | 查询当前会话的历史消息 |
| `send` | 向会话发送一条用户消息，广播 chat 事件 |
| `sessions` | 列出所有会话 |

### Agent Loop

- 串行化执行（每个 Session Key 一个 Lane 队列）
- 流式输出（通过 WebSocket event 推送）
- 支持 OpenAI GPT 模型（可扩展其他 Provider）
- 内存会话管理（可替换为数据库存储）

### Vue3 前端

- WebSocket 连接管理（自动重连）
- 实时聊天界面（Markdown 渲染 + 代码高亮）
- Agent 流式输出显示（打字机效果）
- 连接状态指示

## 扩展方向

1. **多通道集成**：添加 Telegram、Discord、Slack Bot 适配器
2. **多 Agent 路由**：实现 Binding 配置和 Agent 隔离
3. **插件系统**：定义 SPI 接口，支持 JAR 热加载
4. **持久化**：将内存 Session 替换为 SQLite/PostgreSQL
5. **工具系统**：实现 read、write、exec 等内置工具
6. **Memory**：接入向量数据库实现长期记忆
7. **MCP 支持**：作为 MCP Server 和 Client
