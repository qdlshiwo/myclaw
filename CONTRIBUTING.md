# Contributing to MyClaw

MyClaw 是 OpenClaw 的 Java 参考实现，用 Java + Spring Boot + Vue3 构建。

## 环境要求

- JDK 21+
- Gradle 8.x（推荐用 wrapper）
- Node.js 22+
- npm / pnpm

## 项目结构

```
myclaw/
├── myclaw-core/     # 领域模型 + Gateway 协议定义 + 工具抽象
├── myclaw-ai/       # AI Provider（OpenAI/Anthropic）+ Agent Loop + 工具实现
├── myclaw-server/   # Spring Boot Gateway 服务器（WebSocket + HTTP）
├── myclaw-web/      # Vue3 前端（独立项目）
├── settings.gradle.kts
└── build.gradle.kts
```

## 快速开始

```bash
# 1. 编译后端（需要 Java 21）
./gradlew :myclaw-server:compileJava

# 2. 启动后端
cd myclaw-web && npm install && npm run build
cd ..
./gradlew :myclaw-server:bootRun

# 3. 开发前端（可选，前后端分离模式）
cd myclaw-web
npm run dev
```

## 构建打包

```bash
# 构建前端（输出到 myclaw-server/src/main/resources/static）
cd myclaw-web
npm run build

# 构建可运行 JAR
cd ..
./gradlew :myclaw-server:bootJar

# 运行
java -jar myclaw-server/build/libs/myclaw-server-0.1.0-SNAPSHOT.jar
```

## 提交规范

- Commit message 使用中文
- 类型前缀：`feat:`、`fix:`、`refactor:`、`docs:`、`chore:`
- 提交前确保后端编译通过

## 模块边界

- `myclaw-core` 不能依赖 `myclaw-ai` 或 `myclaw-server`
- `myclaw-ai` 不能依赖 `myclaw-server`
- 新增工具时：接口/模型放 core，实现放 myclaw-ai
