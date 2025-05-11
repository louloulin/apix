# APIX Consul Connect 集成

本目录包含 APIX 与 Consul Connect 服务网格的集成资源，实现了 plan7.md 中的 4.1.2 节"服务网格集成"功能。

## 功能概述

APIX 与 Consul Connect 服务网格的集成提供以下功能：

1. **服务发现**：利用 Consul 的服务发现功能，实现服务注册和发现
2. **服务网格**：利用 Consul Connect 的服务网格功能，实现服务间安全通信
3. **意图**：利用 Consul Connect 的意图功能，实现服务间访问控制
4. **配置管理**：利用 Consul 的 KV 存储功能，实现配置管理

## 资源文件

- `service.json`：定义 Consul 服务配置
- `intention.json`：定义 Consul Connect 意图配置
- `service-defaults.json`：定义 Consul Connect 服务默认配置
- `proxy-defaults.json`：定义 Consul Connect 代理默认配置

## 安装和配置

### 前提条件

- 已安装 Consul 服务器
- 已启用 Consul Connect 功能

### 安装步骤

1. 注册服务：

```bash
consul services register service.json
```

2. 创建意图：

```bash
consul intention create -allow web api
```

3. 创建服务默认配置：

```bash
consul config write service-defaults.json
```

4. 创建代理默认配置：

```bash
consul config write proxy-defaults.json
```

## 使用示例

### 服务注册

以下是一个使用 APIX 与 Consul Connect 集成的服务注册示例：

```kotlin
// 创建服务配置
val service = JsonObject()
    .put("name", "my-service")
    .put("address", "127.0.0.1")
    .put("port", 8080)
    .put("tags", JsonArray().add("api").add("v1"))
    .put("meta", JsonObject()
        .put("version", "1.0.0")
    )

// 注册服务
val consulManager = ConsulConnectManager.getInstance(vertx)
consulManager.registerService(service)
    .onSuccess { result ->
        println("服务注册成功: ${result.getString("id")}")
    }
    .onFailure { cause ->
        println("服务注册失败: ${cause.message}")
    }
```

### 启用 Connect

以下是一个使用 APIX 与 Consul Connect 集成的启用 Connect 示例：

```kotlin
// 启用 Connect
val consulManager = ConsulConnectManager.getInstance(vertx)
consulManager.enableConnect("my-service")
    .onSuccess { result ->
        println("Connect 启用成功: ${result.getString("id")}")
    }
    .onFailure { cause ->
        println("Connect 启用失败: ${cause.message}")
    }
```

### 创建意图

以下是一个使用 APIX 与 Consul Connect 集成的创建意图示例：

```kotlin
// 创建意图配置
val intention = JsonObject()
    .put("sourceName", "web")
    .put("destinationName", "api")
    .put("action", "allow")

// 创建意图
val consulManager = ConsulConnectManager.getInstance(vertx)
consulManager.createIntention(intention)
    .onSuccess { result ->
        println("意图创建成功: ${result.getString("id")}")
    }
    .onFailure { cause ->
        println("意图创建失败: ${cause.message}")
    }
```

### 创建服务默认配置

以下是一个使用 APIX 与 Consul Connect 集成的创建服务默认配置示例：

```kotlin
// 创建服务默认配置
val serviceDefaults = JsonObject()
    .put("protocol", "http")
    .put("connectTimeout", 5000)
    .put("maxConnections", 1000)
    .put("maxPendingRequests", 500)

// 创建服务默认配置
val consulManager = ConsulConnectManager.getInstance(vertx)
consulManager.createServiceDefaults("my-service", serviceDefaults)
    .onSuccess { result ->
        println("服务默认配置创建成功: ${result.getString("id")}")
    }
    .onFailure { cause ->
        println("服务默认配置创建失败: ${cause.message}")
    }
```

### 使用服务网格功能

以下是一个使用 APIX 与 Consul Connect 集成的服务网格功能示例：

```kotlin
// 创建安全配置
val securityConfig = JsonObject()
    .put("serviceName", "my-service")
    .put("intention", JsonObject()
        .put("sourceName", "web")
        .put("destinationName", "my-service")
        .put("action", "allow")
    )

// 启用安全功能
val meshFeatures = ServiceMeshFeatures.getInstance(vertx)
meshFeatures.enableSecurity(ServiceMeshFactory.MeshType.CONSUL_CONNECT, securityConfig)
    .onSuccess { result ->
        println("Consul Connect 安全功能启用成功")
    }
    .onFailure { cause ->
        println("Consul Connect 安全功能启用失败: ${cause.message}")
    }
```

## 故障排除

如果遇到问题，可以尝试以下步骤：

1. 检查 Consul 服务状态：

```bash
consul catalog services
consul catalog service my-service
```

2. 检查 Consul Connect 意图：

```bash
consul intention list
```

3. 检查 Consul 配置条目：

```bash
consul config list
```

4. 检查 Consul 日志：

```bash
tail -f /var/log/consul/consul.log
```

5. 检查 APIX 日志：

```bash
kubectl logs <apix-pod-name>
```

## 参考资料

- [Consul 官方文档](https://www.consul.io/docs)
- [Consul Connect 文档](https://www.consul.io/docs/connect)
- [APIX 文档](https://github.com/louloulinlv/apix/docs)
