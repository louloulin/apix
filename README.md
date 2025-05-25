# APIX - AI Agent Gateway

APIX is a high-performance AI Agent Gateway built on GraalVM and Vert.x, designed to manage, secure, and optimize AI agent interactions. Inspired by Kong Gateway's plugin architecture, APIX provides a flexible and extensible platform for routing, transforming, and monitoring AI agent traffic.

[中文版本](#apix---ai-agent-gateway-中文版)

## Features

- **High Performance**: Built on Vert.x reactive framework and GraalVM for native compilation
- **Plugin System**: Modular design with hot-swappable plugins
- **AI-Specific Features**: AI model routing, prompt transformation, response caching, and token usage tracking
- **Admin API**: RESTful API for managing routes, services, and plugins
- **Native Compilation**: GraalVM native image support for low memory footprint and fast startup
- **Clustering Support**: Distributed deployment with Hazelcast and ZooKeeper
- **Monitoring**: Integrated metrics collection and distributed tracing

## Getting Started

### Prerequisites

- JDK 17 or later
- GraalVM CE 22.3 or later (for native compilation)
- Gradle 8.0 or later

### Building the Project

```bash
# Build the project
./gradlew build

# Build native image
./gradlew nativeCompile

# Build with all dependencies included
./gradlew shadowJar
```

### Running the Gateway

```bash
# Run in JVM mode
./gradlew run

# Run native image
./build/native/nativeCompile/apix

# Run with specific configuration
java -jar build/libs/apix-1.0-SNAPSHOT-all.jar -conf config/apix.json
```

## Deployment

### Docker

```bash
# Build Docker image
docker build -t apix:latest .

# Run container
docker run -p 8080:8080 -p 8081:8081 -v $(pwd)/config:/app/config apix:latest
```

### Kubernetes

APIX provides Kubernetes manifests and Helm charts for easy deployment:

```bash
# Using Helm
helm install apix ./helm/apix

# Using kubectl
kubectl apply -k ./k8s/kustomize
```

## Configuration

APIX is configured using a JSON file located at `config/apix.json`. You can customize the gateway by modifying this file.

Example configuration:

```json
{
  "gateway": {
    "host": "0.0.0.0",
    "port": 8080
  },
  "admin": {
    "enabled": true,
    "host": "0.0.0.0",
    "port": 8081
  },
  "plugins": [
    {
      "id": "rate-limiter",
      "type": "rate-limiter",
      "config": {
        "limit": 100,
        "window": 60
      }
    }
  ],
  "routes": [
    {
      "id": "openai-chat",
      "name": "OpenAI Chat Completions",
      "path": "/v1/chat/completions",
      "methods": ["POST"],
      "targetUrl": "https://api.openai.com/v1/chat/completions",
      "plugins": ["rate-limiter"],
      "enabled": true
    }
  ]
}
```

## API Reference

### Admin API

#### Routes Management
```
GET    /admin/routes                # List all routes
POST   /admin/routes                # Create a new route
GET    /admin/routes/{id}           # Get route details
PUT    /admin/routes/{id}           # Update a route
DELETE /admin/routes/{id}           # Delete a route
```

#### Plugins Management
```
GET    /admin/plugins               # List all plugins
```

#### AI-Specific Endpoints
```
GET    /admin/ai/models             # List available AI models
GET    /admin/ai/usage              # Get token usage statistics
```

## Plugin System

APIX includes a flexible plugin system that allows you to extend the gateway's functionality. Plugins can be used to add authentication, rate limiting, request/response transformation, and more.

### Core Plugins

- **Authentication**: API Key, JWT, OAuth2
- **Security**: Rate Limiting, IP Restriction, Request Validation
- **Transformation**: Request/Response Transformation, Prompt Template Injection
- **Logging & Monitoring**: Request Logging, Metrics Collection, Distributed Tracing

### AI-Specific Plugins

- **Prompt Management**: Prompt Validation, Prompt Transformation, Context Window Management
- **AI Response Handling**: Response Caching, Token Usage Tracking, Content Filtering
- **AI Service Management**: Model Routing, Load Balancing, Fallback Strategies

## Performance

APIX is designed for high performance and low latency:

- **Fast Startup**: < 100ms startup time with native image
- **Low Memory Footprint**: < 100MB memory usage
- **High Throughput**: 10,000+ requests per second on modest hardware
- **Low Latency**: < 1ms added latency for gateway processing

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

---

# APIX - AI Agent Gateway (中文版)

APIX 是一个基于 GraalVM 和 Vert.x 构建的高性能 AI 代理网关，旨在管理、保护和优化 AI 代理交互。受 Kong Gateway 插件架构的启发，APIX 提供了一个灵活且可扩展的平台，用于路由、转换和监控 AI 代理流量。

## 特性

- **高性能**：基于 Vert.x 响应式框架和 GraalVM 原生编译
- **插件系统**：模块化设计，支持热插拔插件
- **AI 特定功能**：AI 模型路由、提示词转换、响应缓存和令牌使用跟踪
- **管理 API**：用于管理路由、服务和插件的 RESTful API
- **原生编译**：支持 GraalVM 原生镜像，实现低内存占用和快速启动
- **集群支持**：使用 Hazelcast 和 ZooKeeper 进行分布式部署
- **监控**：集成指标收集和分布式追踪

## 快速开始

### 先决条件

- JDK 17 或更高版本
- GraalVM CE 22.3 或更高版本（用于原生编译）
- Gradle 8.0 或更高版本

### 构建项目

```bash
# 构建项目
./gradlew build

# 构建原生镜像
./gradlew nativeCompile

# 构建包含所有依赖的 JAR 文件
./gradlew shadowJar
```

### 运行网关

```bash
# 在 JVM 模式下运行
./gradlew run

# 运行原生镜像
./build/native/nativeCompile/apix

# 使用特定配置运行
java -jar build/libs/apix-1.0-SNAPSHOT-all.jar -conf config/apix.json
```

## 部署

### Docker

```bash
# 构建 Docker 镜像
docker build -t apix:latest .

# 运行容器
docker run -p 8080:8080 -p 8081:8081 -v $(pwd)/config:/app/config apix:latest
```

### Kubernetes

APIX 提供了 Kubernetes 清单和 Helm 图表，便于部署：

```bash
# 使用 Helm
helm install apix ./helm/apix

# 使用 kubectl
kubectl apply -k ./k8s/kustomize
```

## 配置

APIX 使用位于 `config/apix.json` 的 JSON 文件进行配置。您可以通过修改此文件来自定义网关。

示例配置：

```json
{
  "gateway": {
    "host": "0.0.0.0",
    "port": 8080
  },
  "admin": {
    "enabled": true,
    "host": "0.0.0.0",
    "port": 8081
  },
  "plugins": [
    {
      "id": "rate-limiter",
      "type": "rate-limiter",
      "config": {
        "limit": 100,
        "window": 60
      }
    }
  ],
  "routes": [
    {
      "id": "openai-chat",
      "name": "OpenAI 聊天完成",
      "path": "/v1/chat/completions",
      "methods": ["POST"],
      "targetUrl": "https://api.openai.com/v1/chat/completions",
      "plugins": ["rate-limiter"],
      "enabled": true
    }
  ]
}
```

## API 参考

### 管理 API

#### 路由管理
```
GET    /admin/routes                # 列出所有路由
POST   /admin/routes                # 创建新路由
GET    /admin/routes/{id}           # 获取路由详情
PUT    /admin/routes/{id}           # 更新路由
DELETE /admin/routes/{id}           # 删除路由
```

#### 插件管理
```
GET    /admin/plugins               # 列出所有插件
```

#### AI 特定端点
```
GET    /admin/ai/models             # 列出可用的 AI 模型
GET    /admin/ai/usage              # 获取令牌使用统计
```

## 插件系统

APIX 包含一个灵活的插件系统，允许您扩展网关的功能。插件可用于添加身份验证、速率限制、请求/响应转换等。

### 核心插件

- **身份验证**：API 密钥、JWT、OAuth2
- **安全**：速率限制、IP 限制、请求验证
- **转换**：请求/响应转换、提示模板注入
- **日志和监控**：请求日志、指标收集、分布式追踪

### AI 特定插件

- **提示管理**：提示验证、提示转换、上下文窗口管理
- **AI 响应处理**：响应缓存、令牌使用跟踪、内容过滤
- **AI 服务管理**：模型路由、负载均衡、降级策略

## 性能

APIX 设计用于高性能和低延迟：

- **快速启动**：使用原生镜像启动时间 < 100ms
- **低内存占用**：内存使用量 < 100MB
- **高吞吐量**：在普通硬件上每秒处理 10,000+ 请求
- **低延迟**：网关处理增加的延迟 < 1ms

## 贡献

欢迎贡献！请随时提交 Pull Request。

1. Fork 仓库
2. 创建您的特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交您的更改 (`git commit -m 'Add some amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 打开一个 Pull Request

## 许可证

本项目采用 Apache License 2.0 许可 - 有关详细信息，请参阅 [LICENSE](LICENSE) 文件。
