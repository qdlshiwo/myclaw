# AGENTS.md

AI 协作规范。在修改本仓库前，请先阅读此文档。

## 仓库信息

- Repo: `E:\workspace\github\myclaw`
- 回复中使用相对路径引用代码位置，如 `myclaw-ai/src/...`

## 模块边界

```
myclaw-core       ← 领域模型、协议定义、工具模型
    ↑
myclaw-ai         ← Provider、AgentLoop、工具实现
    ↑
myclaw-server     ← Spring Boot 入口、WebSocket Gateway、配置
    ↑
myclaw-web        ← Vue3 前端（独立项目，不依赖后端源码）
```

**红线**：
- `myclaw-core` 不能依赖 `myclaw-ai`、`myclaw-server`
- `myclaw-ai` 不能依赖 `myclaw-server`
- `myclaw-web` 是完全独立的前端项目，通过 WebSocket 协议与后端通信

## 架构约定

- Core 保持与 AI 运行时无关。Provider、Tool、Session 等抽象接口放在 core
- AI 运行时（AgentLoopService、Provider 实现）放在 myclaw-ai
- Gateway、WebSocket Handler、SessionStore 实现放在 myclaw-server
- 新增工具时：接口/模型放 core，实现放 myclaw-ai，Spring Bean 注册由 `@Component` 扫描自动完成

## 常用命令

### 后端编译

```bash
# 编译（需要 Java 21）
./gradlew :myclaw-server:compileJava

# 构建可运行 JAR（含前端静态资源）
./gradlew :myclaw-server:bootJar
```

### 前端编译

```bash
cd myclaw-web
npm run build
```

### 运行

```bash
# 开发模式（前后端分离）
./gradlew :myclaw-server:bootRun    # 后端 localhost:18789
cd myclaw-web && npm run dev         # 前端 localhost:5173

# 生产模式（前后端一体）
java -jar myclaw-server/build/libs/myclaw-server-0.1.0-SNAPSHOT.jar
```

## 代码规范

### Java

- JDK 21，Spring Boot 3.4.x
- 使用 Lombok（`@Data`、`@Builder`、`@RequiredArgsConstructor`）
- 优先使用 `record` 或 `final` 类做不可变数据
- 接口优先，Provider、Tool、SessionStore 等使用接口抽象
- 异常：业务异常继承自 `RuntimeException`，不要吞掉异常

### 前端

- Vue3 Composition API + `<script setup lang="ts">`
- Pinia 做状态管理
- 单文件组件，样式用 scoped CSS
- WebSocket 通信协议与后端严格对齐

## Git 规范

- Commit message 使用中文
- 类型前缀：`feat:`、`fix:`、`refactor:`、`docs:`、`chore:`
- 提交前确保 `:myclaw-server:compileJava` 和前端 `npm run build` 通过

## Provider 开发规范

- 所有 Provider 实现 `ModelProvider` 接口
- 必须同时支持 `streamChat`（流式）和 `chatComplete`（非流式，用于 Tool Use）
- SSE 解析需兼容国内厂商的变体格式（OpenAI 兼容、Anthropic 兼容）
- 新增 Provider 后，在 `MyClawApplication` 或自动扫描中注册

## Tool 开发规范

- 实现 `Tool` 接口
- 参数 Schema 使用 Jackson `ObjectNode` 构建
- 工具类加 `@Component`，由 `ToolRegistry` 自动收集
- 危险操作（写文件、执行 Shell）需在实现中校验路径和权限

## 测试

- 优先在后端编译层面验证，确保 `./gradlew :myclaw-server:compileJava` 通过
- 前端构建 `npm run build` 作为 UI 变更的基础验证
