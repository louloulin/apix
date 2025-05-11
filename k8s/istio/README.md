# APIX Istio 集成

本目录包含 APIX 与 Istio 服务网格的集成资源，实现了 plan7.md 中的 4.1.2 节"服务网格集成"功能。

## 功能概述

APIX 与 Istio 服务网格的集成提供以下功能：

1. **流量管理**：利用 Istio 的流量管理功能，实现高级路由、负载均衡和流量控制
2. **安全**：利用 Istio 的安全功能，实现 mTLS、授权策略和身份验证
3. **可观测性**：利用 Istio 的可观测性功能，实现分布式追踪、监控和日志

## 资源文件

- `gateway.yaml`：定义 Istio Gateway 资源，用于接收外部流量
- `virtual-service.yaml`：定义 Istio VirtualService 资源，用于路由流量
- `destination-rule.yaml`：定义 Istio DestinationRule 资源，用于配置负载均衡和连接池
- `service-entry.yaml`：定义 Istio ServiceEntry 资源，用于注册外部服务
- `authorization-policy.yaml`：定义 Istio AuthorizationPolicy 资源，用于配置访问控制
- `peer-authentication.yaml`：定义 Istio PeerAuthentication 资源，用于配置 mTLS
- `telemetry.yaml`：定义 Istio Telemetry 资源，用于配置可观测性

## 安装和配置

### 前提条件

- 已安装 Kubernetes 集群
- 已安装 Istio 服务网格

### 安装步骤

1. 安装 Istio Gateway：

```bash
kubectl apply -f gateway.yaml
```

2. 安装 VirtualService：

```bash
kubectl apply -f virtual-service.yaml
```

3. 安装 DestinationRule：

```bash
kubectl apply -f destination-rule.yaml
```

4. 安装 ServiceEntry：

```bash
kubectl apply -f service-entry.yaml
```

5. 安装安全策略：

```bash
kubectl apply -f authorization-policy.yaml
kubectl apply -f peer-authentication.yaml
```

6. 安装可观测性配置：

```bash
kubectl apply -f telemetry.yaml
```

## 使用示例

### 流量管理

以下是一个使用 APIX 与 Istio 集成的流量管理示例：

```kotlin
// 创建流量管理配置
val trafficConfig = JsonObject()
    .put("virtualService", JsonObject()
        .put("name", "my-virtual-service")
        .put("hosts", JsonArray().add("api.example.com"))
        .put("gateways", JsonArray().add("my-gateway"))
        .put("http", JsonArray()
            .add(JsonObject()
                .put("route", JsonArray()
                    .add(JsonObject()
                        .put("destination", JsonObject()
                            .put("host", "my-service")
                            .put("port", JsonObject().put("number", 8080))
                        )
                    )
                )
            )
        )
    )
    .put("destinationRule", JsonObject()
        .put("name", "my-destination-rule")
        .put("host", "my-service")
        .put("trafficPolicy", JsonObject()
            .put("loadBalancer", JsonObject()
                .put("simple", "ROUND_ROBIN")
            )
        )
    )

// 启用流量管理功能
val meshFeatures = ServiceMeshFeatures.getInstance(vertx)
meshFeatures.enableTrafficManagement(ServiceMeshFactory.MeshType.ISTIO, trafficConfig)
    .onSuccess { result ->
        println("Istio 流量管理功能启用成功")
    }
    .onFailure { cause ->
        println("Istio 流量管理功能启用失败: ${cause.message}")
    }
```

### 安全

以下是一个使用 APIX 与 Istio 集成的安全示例：

```kotlin
// 创建安全配置
val securityConfig = JsonObject()
    .put("namespace", "default")
    .put("mtlsMode", "STRICT")
    .put("authorizationPolicy", JsonObject()
        .put("name", "my-authorization-policy")
        .put("namespace", "default")
        .put("selector", JsonObject().put("app", "my-app"))
        .put("action", "ALLOW")
        .put("rules", JsonArray()
            .add(JsonObject()
                .put("from", JsonArray()
                    .add(JsonObject()
                        .put("source", JsonObject()
                            .put("namespaces", JsonArray().add("default"))
                        )
                    )
                )
                .put("to", JsonArray()
                    .add(JsonObject()
                        .put("operation", JsonObject()
                            .put("methods", JsonArray().add("GET").add("POST"))
                            .put("paths", JsonArray().add("/api/*"))
                        )
                    )
                )
            )
        )
    )

// 启用安全功能
val meshFeatures = ServiceMeshFeatures.getInstance(vertx)
meshFeatures.enableSecurity(ServiceMeshFactory.MeshType.ISTIO, securityConfig)
    .onSuccess { result ->
        println("Istio 安全功能启用成功")
    }
    .onFailure { cause ->
        println("Istio 安全功能启用失败: ${cause.message}")
    }
```

### 可观测性

以下是一个使用 APIX 与 Istio 集成的可观测性示例：

```kotlin
// 创建可观测性配置
val observabilityConfig = JsonObject()
    .put("tracing", JsonObject()
        .put("enabled", true)
        .put("provider", "zipkin")
    )
    .put("metrics", JsonObject()
        .put("enabled", true)
        .put("provider", "prometheus")
    )

// 启用可观测性功能
val meshFeatures = ServiceMeshFeatures.getInstance(vertx)
meshFeatures.enableObservability(ServiceMeshFactory.MeshType.ISTIO, observabilityConfig)
    .onSuccess { result ->
        println("Istio 可观测性功能启用成功")
    }
    .onFailure { cause ->
        println("Istio 可观测性功能启用失败: ${cause.message}")
    }
```

## 故障排除

如果遇到问题，可以尝试以下步骤：

1. 检查 Istio 资源状态：

```bash
kubectl get gateways
kubectl get virtualservices
kubectl get destinationrules
kubectl get serviceentries
kubectl get authorizationpolicies
kubectl get peerauthentications
```

2. 检查 Istio 代理日志：

```bash
kubectl logs <pod-name> -c istio-proxy
```

3. 检查 APIX 日志：

```bash
kubectl logs <apix-pod-name>
```

## 参考资料

- [Istio 官方文档](https://istio.io/latest/docs/)
- [APIX 文档](https://github.com/louloulinlv/apix/docs)
