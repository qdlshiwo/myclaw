# OpenClaw 架构分析与 Java 版实现方案

## 一、OpenClaw 源码架构总览

OpenClaw 是一个**自托管的多通道 AI 网关**，核心定位是将各种即时通讯渠道（WhatsApp、Telegram、Slack、Discord 等）连接到 AI Agent。整个项目采用 TypeScript/Node.js 编写，采用单体架构但具备高度模块化和插件化能力。

### 1.1 整体架构图

```
+-------------------+        +---------------------+        +------------------+
|   Chat Apps       |        |    Gateway Server   |        |   AI Providers   |
|  (WhatsApp/       |<------>|  (Node.js/TS Core)  |<------>|  (OpenAI/        |
|   Telegram/       |  WS/   |                     |  HTTP  |   Anthropic/     |
|   Slack/Discord...) | HTTP |  - Session Manager  |  /WS   |   Local Models)  |
+-------------------+        |  - Channel Router   |        +------------------+
         ^                   |  - Agent Loop       |               ^
         |                   |  - Plugin System    |               |
         v                   |  - Tool Registry    |               v
+-------------------+        +---------------------+        +------------------+
|  Web Control UI   |        |   File System State |        |   MCP Servers    |
|  (Lit/Vanilla JS) |<------>|  - ~/.openclaw/     |<------>|   (External)     |
+-------------------+   WS   |  - sessions/        |        +------------------+
                             |  - agents/          |
                             |  - credentials/     |
                             +---------------------+
                                      ^
                                      |
+-------------------+        +---------------------+
|  Mobile Nodes     |        |   Plugin Ecosystem  |
|  (iOS/Android)    |<------>|  - Channel Plugins  |
+-------------------+   WS   |  - Memory Plugins   |
                             |  - Provider Plugins |
                             +---------------------+
```

### 1.2 核心模块划分

| 模块 | 路径 | 职责 |
|------|------|------|
| **Gateway** | `src/gateway/` | 核心网关服务器，WebSocket/HTTP 服务端，维护所有连接和状态 |
| **Channels** | `src/channels/` | 消息通道抽象层，内置通道和插件通道的注册、路由、消息收发 |
| **Agents** | `src/agents/` | AI Agent 运行时，包含模型调用、工具执行、Prompt 组装、Session 管理等 |
| **Plugins** | `src/plugins/` | 插件系统，支持通道插件、Provider 插件、Memory 插件、Hook 扩展 |
| **Auto-Reply** | `src/auto-reply/` | 自动回复引擎，处理入站消息的智能路由和响应编排 |
| **CLI** | `src/cli/` | 命令行接口，提供 `openclaw` 命令的各子命令实现 |
| **Config** | `src/config/` | 配置管理，JSON5 配置文件的读写、热重载、校验 |
| **Memory** | `src/memory/` | 记忆系统，支持 QMD、内置记忆、Honcho 等多种后端 |
| **Tools** | `src/tools/` | 工具注册表，内置工具和插件工具的加载与调用 |
| **Web/UI** | `ui/` | 浏览器控制面板，基于 Lit 的 Web Components 实现 |
| **Extensions** | `extensions/` | 官方扩展包，每个目录是一个独立的 npm 包/插件 |

---

## 二、关键核心机制详解

### 2.1 Gateway 服务器（网关核心）

Gateway 是一个**长期运行的守护进程**，是所有组件的单一数据源：

- **通信协议**：WebSocket + HTTP 双协议，默认端口 `18789`
- **连接类型**：
  - **Control Plane Clients**：macOS App、CLI、Web UI，通过 WS 发送请求和订阅事件
  - **Nodes**：iOS/Android/Headless 节点，WS 连接但声明 `role: node`，提供设备能力（相机、Canvas、位置等）
  - **Channel Plugins**：各消息平台的连接适配器
- **Wire Protocol**：
  - 首帧必须是 `connect` 握手
  - 请求：`{type:"req", id, method, params}` → 响应：`{type:"res", id, ok, payload|error}`
  - 事件推送：`{type:"event", event, payload, seq?}`
  - 基于 TypeBox 的 JSON Schema 校验
- **认证**：Shared-secret Token 或 Password，支持 Tailscale/VPN 免密模式
- **设备配对**：新设备需配对审批，签发 Device Token，本地回环可自动审批

### 2.2 Agent Loop（智能体执行循环）

Agent Loop 是 OpenClaw 的核心认知引擎，一个完整的 Loop 包含：

```
Inbound Message
      |
      v
Session Resolve  ----->  Session Write Lock
      |                          |
      v                          v
Prompt Assembly  <----  Skills + Bootstrap + Context
      |
      v
Model Inference  ----->  Streaming Deltas
      |
      v
Tool Execution   ----->  Tool Start/Update/End Events
      |
      v
Reply Shaping    ----->  Lifecycle End/Event
      |
      v
Persistence      ----->  Transcript Write (JSONL)
```

**关键设计点**：
- **串行执行**：每个 Session Key 的运行串行化，通过 Lane 队列防止竞态
- **流式输出**：Assistant delta 实时流式推送到客户端
- **工具执行**：支持 Bash、文件读写、Web 搜索、图像生成、子 Agent 派生等
- **Prompt 组装**：Base Prompt + Skills Prompt + Bootstrap Context + 系统提示报告
- **Compaction（压缩）**：长会话自动摘要，释放上下文窗口
- **超时控制**：默认 Agent 超时 48 小时，模型空闲超时默认 120 秒

### 2.3 Channel 系统（多通道消息）

Channel 是消息平台的抽象接口：

- **Built-in Channels**：WhatsApp (Baileys)、Telegram (grammY)、Slack、Discord、Signal、iMessage 等直接内置
- **Plugin Channels**：Matrix、Nostr、Twitch、Zalo、Feishu 等通过插件加载
- **Turn（轮次）模型**：每条入站消息包装为一个 ChannelTurn，经过准入检查、路由解析、Agent 调度、回复投递
- **Binding（绑定）**：通过 `bindings` 配置将 `(channel, accountId, peer)` 映射到特定 Agent
- **路由优先级**：peer > parentPeer > guildId/roles > teamId > accountId > channel > default agent

### 2.4 Multi-Agent 路由

OpenClaw 支持在单个 Gateway 中运行**多个隔离的 Agent**：

- 每个 Agent 拥有独立的 **workspace**、**auth profiles**、**model registry**、**session store**
- Agent 状态目录：`~/.openclaw/agents/<agentId>/`
- 默认单 Agent 模式（`agentId = main`）
- 会话隔离：DM 默认共享会话，多用户场景建议启用 `dmScope: per-channel-peer`

### 2.5 Plugin 系统

OpenClaw 的扩展能力极其丰富：

**两种插件风格**：
1. **Code Plugins**：运行 OpenClaw 插件代码，适用于深度运行时扩展（Provider、Channel、Tool、Hook）
2. **Bundle Plugins**：打包外部能力（Skills、MCP Servers、配置），接口更稳定、更安全

**核心扩展点**：
- `before_model_resolve`：模型选择前干预
- `before_prompt_build`：Prompt 构建时注入上下文
- `before_agent_reply`：拦截并替代 LLM 回复
- `before_tool_call` / `after_tool_call`：工具调用前后钩子
- `message_received` / `message_sending` / `message_sent`：消息生命周期
- `gateway_start` / `gateway_stop`：网关生命周期

**插件分发**：npm 包或本地路径加载，官方插件市场为 ClawHub。

### 2.6 Session 管理

- **存储位置**：`~/.openclaw/agents/<agentId>/sessions/sessions.json`（索引）+ `<sessionId>.jsonl`（对话记录）
- **生命周期**：每日重置（默认凌晨 4 点）、空闲重置、手动 `/new` 或 `/reset`
- **会话键**：按来源区分（DM、Group、Room、Cron、Webhook）
- **持久化格式**：JSON Lines，每行一条消息记录

---

## 三、技术栈与依赖分析

| 层次 | 技术选型 |
|------|----------|
| 运行时 | Node.js 22+ (ES Module) |
| 语言 | TypeScript 5.x |
| WS 服务器 | 原生 `ws` 库 |
| HTTP 服务器 | Node.js 原生 `http` + 自定义路由 |
| 配置格式 | JSON5 |
| 协议校验 | TypeBox → JSON Schema |
| 进程管理 | 子进程 spawn（Agent 运行时、Bash 工具） |
| 包管理 | pnpm workspace（monorepo） |
| UI | Lit (Web Components) + 原生 CSS |
| 移动端 | Swift (iOS) / Kotlin (Android) |
| 构建 | tsup / tsdown |

---

## 四、Java 版实现方案

基于以上架构分析，以下是用 Java 重新实现 OpenClaw 的完整方案。

### 4.1 技术栈选型

| 组件 | 推荐技术 | 说明 |
|------|----------|------|
| 后端框架 | **Spring Boot 3.x** | 生态成熟，依赖注入完善，与 Java 社区深度整合 |
| WebSocket | **Spring WebSocket** (STOMP) 或原生 `jakarta.websocket` | Gateway 核心通信层 |
| HTTP API | **Spring Web MVC** 或 **WebFlux** | 控制面板 HTTP 接口，WebFlux 更适合高并发场景 |
| 配置管理 | **Spring Boot Config** + 自定义 JSON5 解析 | 可用 `json5-java` 或 Jackson 自定义解析器 |
| 数据库 | **H2/SQLite**（开发）+ **PostgreSQL**（生产） | Session、配置、Credential 持久化 |
| 缓存 | **Caffeine** | 内存缓存，Token、模型目录等 |
| 任务队列 | **Spring Scheduler** + **CompletableFuture** | Session Lane 串行队列 |
| 进程执行 | **Apache Commons Exec** 或 Java `ProcessBuilder` | Bash 工具、Agent 子进程 |
| HTTP 客户端 | **WebClient** (WebFlux) 或 **OkHttp** | Provider API 调用 |
| 事件总线 | **Spring ApplicationEvent** 或 **Reactor EventBus** | 内部事件解耦 |
| 安全 | **Spring Security** | Token 认证、密码加密 |
| 构建工具 | **Gradle** (Kotlin DSL) | 比 Maven 更灵活，适合多模块 |
| 前端 | **Vue 3 + Vite + TypeScript + Pinia** | 现代化前端栈 |
| UI 组件库 | **Element Plus** 或 **Naive UI** | 管理后台风格 |
| 前端通信 | **原生 WebSocket API** | 直接对接 Gateway 协议 |

### 4.2 项目模块结构（Gradle Multi-Module）

```
myclaw/
├── myclaw-server/                    # Gateway 核心服务器（Spring Boot 入口）
│   ├── src/main/java/com/myclaw/server/
│   │   ├── MyClawApplication.java
│   │   ├── config/                   # 配置加载、热重载
│   │   ├── websocket/                # WebSocket 握手、连接管理、协议处理
│   │   ├── http/                     # HTTP API（Control UI 静态资源、REST 接口）
│   │   ├── auth/                     # 认证、设备配对、Token 管理
│   │   └── bootstrap/                # 启动流程、服务组装
│   └── src/main/resources/
│       ├── static/                   # 打包后的 Vue3 前端
│       └── application.yml
│
├── myclaw-core/                      # 核心领域模型与公共组件
│   ├── src/main/java/com/myclaw/core/
│   │   ├── model/                    # 领域模型（Message, Session, Agent, Channel...）
│   │   ├── protocol/                 # Gateway 协议定义（Frame, Request, Response, Event）
│   │   ├── exception/                # 业务异常体系
│   │   └── util/                     # 工具类
│   └── src/main/resources/
│
├── myclaw-gateway/                   # Gateway 业务逻辑层
│   ├── src/main/java/com/myclaw/gateway/
│   │   ├── server/                   # GatewayServer 实现
│   │   ├── session/                  # SessionManager、SessionStore、Transcript 读写
│   │   ├── agent/                    # Agent Loop 编排、Prompt 组装
│   │   ├── routing/                  # Binding 路由、多 Agent 路由
│   │   ├── channel/                  # Channel 抽象、注册表、生命周期
│   │   ├── plugin/                   # Plugin 加载、注册、Hook 调用
│   │   ├── tool/                     # Tool Registry、Tool Executor
│   │   ├── memory/                   # Memory 抽象、多种后端实现
│   │   ├── cron/                     # 定时任务服务
│   │   └── events/                   # 事件发布/订阅
│   └── src/main/resources/
│
├── myclaw-channels/                  # 通道实现（可独立扩展）
│   ├── src/main/java/com/myclaw/channels/
│   │   ├── api/                      # Channel Plugin SPI 接口
│   │   ├── builtin/                  # 内置通道（WebChat）
│   │   ├── telegram/                 # Telegram Bot API 集成
│   │   ├── discord/                  # Discord4J 集成
│   │   ├── slack/                    # Slack Bolt SDK 集成
│   │   └── whatsapp/                 # WhatsApp Web / Baileys 桥接
│   └── build.gradle.kts
│
├── myclaw-ai/                        # AI Provider 与 Agent 运行时
│   ├── src/main/java/com/myclaw/ai/
│   │   ├── provider/                 # Provider SPI（OpenAI、Anthropic、Ollama 等）
│   │   ├── provider/openai/          # OpenAI 实现
│   │   ├── provider/anthropic/       # Anthropic 实现
│   │   ├── provider/ollama/          # Ollama 本地模型
│   │   ├── runtime/                  # Agent Loop 运行时、流式处理
│   │   ├── prompt/                   # Prompt 组装、System Prompt 构建
│   │   ├── tool/                     # 内置工具实现（Bash、Read、Write、Edit...）
│   │   ├── compaction/               # 会话压缩/摘要
│   │   └── streaming/                # SSE/流式响应处理
│   └── build.gradle.kts
│
├── myclaw-plugins-sdk/               # 插件开发 SDK（供第三方使用）
│   ├── src/main/java/com/myclaw/sdk/
│   │   ├── plugin/                   # Plugin 基类、注解
│   │   ├── channel/                  # ChannelPlugin 接口
│   │   ├── provider/                 # ProviderPlugin 接口
│   │   ├── hook/                     # Hook 注册注解
│   │   └── tool/                     # Tool 定义注解
│   └── build.gradle.kts
│
├── myclaw-cli/                       # 命令行工具（可选）
│   ├── src/main/java/com/myclaw/cli/
│   └── build.gradle.kts
│
└── myclaw-web/                       # Vue3 前端项目
    ├── src/
    │   ├── components/               # 通用组件
    │   ├── views/                    # 页面视图
    │   │   ├── DashboardView.vue     # 仪表盘
    │   │   ├── ChatView.vue          # 聊天界面
    │   │   ├── SessionsView.vue      # 会话管理
    │   │   ├── ChannelsView.vue      # 通道配置
    │   │   ├── AgentsView.vue        # Agent 管理
    │   │   ├── PluginsView.vue       # 插件市场
    │   │   └── SettingsView.vue      # 系统设置
    │   ├── stores/                   # Pinia 状态管理
    │   │   ├── gatewayStore.ts       # Gateway WS 连接、全局状态
    │   │   ├── chatStore.ts          # 聊天状态、消息列表
    │   │   ├── sessionStore.ts       # 会话列表
    │   │   └── configStore.ts        # 配置管理
    │   ├── composables/              # 组合式函数
    │   │   ├── useGateway.ts         # WebSocket 连接逻辑
    │   │   ├── useAgentStream.ts     # Agent 流式消息处理
    │   │   └── useChannel.ts         # 通道操作
    │   ├── api/                      # HTTP API 客户端
    │   ├── types/                    # TypeScript 类型定义（与后端协议对齐）
    │   ├── router/                   # Vue Router
    │   └── App.vue
    ├── index.html
    └── vite.config.ts
```

### 4.3 核心类设计（Java）

#### 4.3.1 Gateway Server

```java
@Component
public class GatewayServer {
    private final GatewayConfig config;
    private final ChannelRegistry channelRegistry;
    private final SessionManager sessionManager;
    private final AgentLoopService agentLoopService;
    private final PluginManager pluginManager;
    private final WebSocketConnectionManager wsManager;
    private final EventPublisher eventPublisher;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        // 1. 加载配置
        // 2. 启动插件系统
        // 3. 初始化通道连接
        // 4. 启动 WS/HTTP 服务器
        // 5. 启动 Cron 服务
    }

    public AgentRunResult executeAgent(AgentRequest request) {
        // 串行化：按 sessionKey 获取 Lane 队列
        return agentLoopService.run(request);
    }
}
```

#### 4.3.2 WebSocket 协议处理

```java
@Component
public class GatewayWebSocketHandler extends TextWebSocketHandler {
    private final GatewayProtocolHandler protocolHandler;
    private final AuthService authService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 等待 connect 握手帧
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        GatewayFrame frame = parseFrame(message.getPayload());
        switch (frame.getType()) {
            case "req" -> handleRequest(session, frame.toRequest());
            case "event" -> handleEvent(session, frame.toEvent());
        }
    }

    private void handleRequest(WebSocketSession session, GatewayRequest req) {
        GatewayMethodHandler handler = methodRegistry.get(req.getMethod());
        GatewayResponse res = handler.handle(req);
        session.sendMessage(new TextMessage(serialize(res)));
    }
}
```

#### 4.3.3 Agent Loop 服务

```java
@Service
public class AgentLoopService {
    private final Map<String, SessionLane> lanes = new ConcurrentHashMap<>();
    private final PromptAssembler promptAssembler;
    private final ModelProviderRegistry providerRegistry;
    private final ToolExecutor toolExecutor;
    private final PluginHookRunner hookRunner;

    public AgentRunResult run(AgentRequest request) {
        SessionLane lane = lanes.computeIfAbsent(
            request.getSessionKey(), k -> new SessionLane()
        );

        return lane.submit(() -> {
            // 1. 获取 Session 写锁
            // 2. 加载 Skills、Bootstrap
            // 3. 运行 before_prompt_build hooks
            // 4. 组装 System Prompt + Messages
            // 5. 调用 LLM（流式）
            // 6. 处理工具调用
            // 7. 构建回复并持久化
            // 8. 释放写锁
            return executeLoop(request);
        });
    }
}
```

#### 4.3.4 Channel 抽象

```java
public interface ChannelPlugin {
    String getChannelId();
    void initialize(ChannelConfig config);
    void start();
    void shutdown();
    CompletableFuture<Void> sendMessage(OutboundMessage message);
    // 事件回调由 Gateway 注册
}

public interface ChannelEventListener {
    void onInboundMessage(InboundMessage message);
    void onTypingIndicator(TypingEvent event);
    void onPresenceChange(PresenceEvent event);
}
```

#### 4.3.5 多 Agent 路由

```java
@Service
public class AgentRouter {
    private final List<Binding> bindings;
    private final Map<String, AgentContext> agents;

    public AgentContext resolve(InboundMessage message) {
        // 按优先级匹配 Binding
        for (Binding binding : bindings) {
            if (matches(binding, message)) {
                return agents.get(binding.getAgentId());
            }
        }
        return getDefaultAgent();
    }

    private boolean matches(Binding binding, InboundMessage msg) {
        return binding.getChannel().equals(msg.getChannelId())
            && (binding.getAccountId() == null || binding.getAccountId().equals(msg.getAccountId()))
            && (binding.getPeer() == null || binding.getPeer().matches(msg.getPeer()));
    }
}
```

### 4.4 数据持久化方案

OpenClaw 使用文件系统（JSON/JSONL）作为默认存储，Java 版可保留此设计或引入轻量级数据库：

| 数据类型 | 原始实现 | Java 版建议 |
|----------|----------|-------------|
| 主配置 | `~/.myclaw/myclaw.json5` | 文件 + 内存缓存，启动时加载 |
| 会话索引 | `sessions.json` | SQLite/H2 表 `sessions` |
| 对话记录 | `<sessionId>.jsonl` | 文件保持 JSONL 或 SQLite 表 `messages` |
| Credential | 文件系统加密存储 | JCE 加密 + 文件/数据库存储 |
| Agent 状态 | 文件系统 | 数据库表 `agents`、`agent_configs` |
| Plugin 元数据 | 文件系统 | 文件系统（与插件文件共存） |

**推荐**：使用 **SQLite** 作为默认嵌入式数据库（零配置），同时支持外部 PostgreSQL 切换。

### 4.5 与 OpenClaw 协议兼容

为保持与现有生态（iOS/Android App、现有 Skills）的兼容，Java 版 Gateway 应**复用 OpenClaw 的 Wire Protocol**：

- 完全相同的 WebSocket JSON 帧格式
- 相同的 Method 和 Event 命名
- 相同的 TypeBox/JSON Schema 校验逻辑（可用 `json-schema-validator` 库）
- 相同的认证流程（connect → challenge → token）
- 相同的 Session Key 生成规则

这样 Vue3 前端、移动端 Node、第三方插件都可以无缝接入。

---

## 五、Vue3 前端实现建议

### 5.1 前端架构

```
myclaw-web/
├── src/
│   ├── api/
│   │   └── gateway.ts          # WebSocket 客户端封装
│   ├── views/
│   │   ├── ChatView.vue        # 主聊天界面（仿 OpenClaw WebChat）
│   │   ├── DashboardView.vue   # 仪表盘、状态概览
│   │   ├── SessionView.vue     # 会话历史、搜索
│   │   └── SettingsView.vue    # 配置编辑（JSON5 可视化表单）
│   ├── components/
│   │   ├── ChatMessage.vue     # 单条消息渲染（Markdown + 代码高亮）
│   │   ├── ChatInput.vue       # 输入框、附件上传
│   │   ├── AgentStream.vue     # Agent 流式输出展示
│   │   ├── ToolCallCard.vue    # 工具调用结果卡片
│   │   ├── ChannelList.vue     # 通道状态列表
│   │   └── SessionSidebar.vue  # 会话侧边栏
│   ├── stores/
│   │   ├── gateway.ts          # Gateway 连接、认证、全局事件
│   │   ├── chat.ts             # 当前会话消息、流式状态
│   │   └── config.ts           # 配置镜像、编辑
│   ├── composables/
│   │   ├── useGatewayWs.ts     # WS 重连、心跳、帧解析
│   │   ├── useAgentStream.ts   # 流式 assistant/tool/lifecycle 事件聚合
│   │   └── useMarkdown.ts      # Markdown 渲染 + 代码高亮
│   └── types/
│       └── protocol.ts         # 与后端协议对齐的 TypeScript 类型
```

### 5.2 关键前端逻辑

**WebSocket 连接管理**：

```typescript
// composables/useGatewayWs.ts
export function useGatewayWs() {
  const ws = ref<WebSocket | null>(null);
  const connected = ref(false);
  const events = new EventEmitter();

  function connect(url: string, token: string) {
    ws.value = new WebSocket(url);
    ws.value.onopen = () => {
      // 发送 connect 握手
      send({ type: 'req', id: 'connect-1', method: 'connect', params: { auth: { token } } });
    };
    ws.value.onmessage = (e) => {
      const frame = JSON.parse(e.data);
      if (frame.type === 'event') {
        events.emit(frame.event, frame.payload);
      }
    };
  }

  function callAgent(params: AgentParams): ReadableStream<AgentEvent> {
    return new ReadableStream({
      start(controller) {
        const id = generateId();
        const handler = (payload: any) => controller.enqueue(payload);
        events.on('agent', handler);
        send({ type: 'req', id, method: 'agent', params });
      }
    });
  }

  return { connect, connected, events, callAgent };
}
```

**流式消息渲染**：

```vue
<!-- components/AgentStream.vue -->
<template>
  <div class="agent-stream">
    <div v-if="reasoningText" class="reasoning-block">
      <pre>{{ reasoningText }}</pre>
    </div>
    <div class="assistant-text" v-html="renderedHtml"></div>
    <ToolCallCard v-for="tool in tools" :key="tool.id" :tool="tool" />
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{ stream: AgentEvent[] }>();

const reasoningText = computed(() => /* 提取 reasoning 块 */);
const assistantText = computed(() => /* 聚合 assistant delta */);
const renderedHtml = computed(() => useMarkdown(assistantText.value));
const tools = computed(() => /* 提取 tool 事件 */);
</script>
```

### 5.3 UI 设计参考

OpenClaw 的 Control UI 是功能密集型的运维面板，Vue3 版建议：

- **侧边导航**：Chat / Sessions / Channels / Agents / Plugins / Settings
- **聊天界面**：左侧会话列表，右侧消息流（支持 Markdown、代码块、图片、工具卡片）
- **配置编辑器**：表单化编辑 `myclaw.json5`，带校验和文档提示
- **实时状态**：通道健康度、Agent 运行状态、系统日志流

---

## 六、开发路线图建议

### 阶段一：MVP Gateway（1-2 个月）

1. Spring Boot 基础框架 + WebSocket 服务器
2. 实现完整的 OpenClaw Wire Protocol（connect、agent、chat、send 方法）
3. 文件系统配置管理（加载 `myclaw.json5`）
4. 单 Agent 模式 + Session 管理（文件持久化）
5. WebChat 内置通道（最简单的浏览器聊天）
6. OpenAI Provider 集成
7. 基础工具：read、write、exec
8. Vue3 前端：聊天界面 + 配置面板

### 阶段二：多通道与多 Agent（1 个月）

1. Telegram Bot 通道集成
2. Discord 通道集成
3. Multi-Agent 路由 + Binding 系统
4. DM 隔离配置
5. 插件加载机制（JAR 热加载或 SPI）

### 阶段三：插件生态与高级功能（1-2 个月）

1. 完整的 Plugin SDK
2. Hook 系统（before_prompt_build、before_tool_call 等）
3. Memory 插件接口（向量搜索可用 SQLite-vss 或 LanceDB Java 绑定）
4. Skills 系统
5. MCP 协议支持
6. Cron 定时任务
7. 移动端配对流程

### 阶段四：打磨与优化（持续）

1. 配置热重载
2. 会话压缩（Compaction）
3. 模型故障转移（Failover）
4. OAuth 认证流
5. 沙箱执行（Docker/Java SecurityManager）
6. 遥测与诊断

---

## 七、关键风险与注意事项

1. **Pi Agent Core 是闭源的**：OpenClaw 内嵌的 `pi-agent-core` 是闭源二进制（通过 RPC 调用）。Java 版需要自行实现 Agent Loop 或用其他开源 Agent 框架替代（如 LangChain4j、Spring AI）。
2. **通道 SDK 差异**：WhatsApp（Baileys 是 Node.js 库）在 Java 中没有直接等价物，需用 WhatsApp Business API 或自行桥接。
3. **插件兼容性**：OpenClaw 插件是 npm 包/TypeScript。Java 版插件需重新定义 SPI，无法直接复用现有插件。
4. **协议兼容性**：如追求与现有移动端 App 兼容，需严格遵循 OpenClaw 的 WebSocket 协议和配对流程。
5. **文件系统持久化**：Java 的文件锁和并发写需仔细处理，OpenClaw 使用 `proper-lockfile` 实现跨进程 Session 写锁。

---

## 八、参考资源

- OpenClaw 源码：`E:/workspace/github/openclaw`
- OpenClaw 架构文档：`docs/concepts/architecture.md`、`docs/concepts/agent-loop.md`
- OpenClaw 协议文档：`docs/gateway/protocol.md`
- Spring AI（Java AI 框架）：https://spring.io/projects/spring-ai
- LangChain4j：https://github.com/langchain4j/langchain4j
- Vue3 文档：https://vuejs.org/
