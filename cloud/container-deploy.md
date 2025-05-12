# 容器集群部署示例

本文档提供了使用 APIX 多云部署管理器进行容器集群部署的示例，实现了 plan7.md 中的 4.1.3 节"云原生部署"功能。

## 概述

APIX 支持将 API 网关部署到各大云提供商的容器集群平台，包括：

- Amazon EKS (Elastic Kubernetes Service)
- Azure AKS (Azure Kubernetes Service)
- Google GKE (Google Kubernetes Engine)
- 阿里云 ACK (Alibaba Cloud Container Service for Kubernetes)
- 腾讯云 TKE (Tencent Kubernetes Engine)
- 华为云 CCE (Cloud Container Engine)

本示例将演示如何将 APIX 部署到 Amazon EKS。

## 前提条件

- 已安装 APIX
- 已配置 AWS 账号和凭证
- 已安装 AWS CLI 和 kubectl
- 已安装 eksctl

## 部署步骤

### 1. 创建 AWS 云提供商

首先，我们需要创建一个 AWS 云提供商：

```kotlin
// 创建 AWS 云提供商配置
val providerConfig = JsonObject()
    .put("accessKey", "YOUR_AWS_ACCESS_KEY")
    .put("secretKey", "YOUR_AWS_SECRET_KEY")
    .put("region", "us-east-1")

// 创建 AWS 云提供商
val deployManager = MultiCloudDeployManager.getInstance(vertx)
deployManager.createCloudProvider(CloudProviderType.AWS, providerConfig)
    .onSuccess { result ->
        println("AWS 云提供商创建成功: $result")
    }
    .onFailure { cause ->
        println("AWS 云提供商创建失败: ${cause.message}")
    }
```

### 2. 创建容器集群部署配置

接下来，我们需要创建一个容器集群部署配置：

```kotlin
// 创建容器集群部署配置
val deployConfig = JsonObject()
    .put("name", "APIX Cluster")
    .put("providerType", "AWS")
    .put("deployType", "container")
    .put("region", "us-east-1")
    .put("clusterConfig", JsonObject()
        .put("name", "apix-cluster")
        .put("version", "1.27")
        .put("nodeCount", 3)
        .put("nodeType", "t3.medium")
        .put("vpcConfig", JsonObject()
            .put("subnetIds", JsonArray()
                .add("subnet-12345678")
                .add("subnet-87654321")
            )
            .put("securityGroupIds", JsonArray()
                .add("sg-12345678")
            )
        )
        .put("logging", JsonObject()
            .put("clusterLogging", JsonArray()
                .add("api")
                .add("audit")
                .add("authenticator")
                .add("controllerManager")
                .add("scheduler")
            )
        )
    )

// 创建部署配置
deployManager.createDeployConfig(deployConfig)
    .onSuccess { result ->
        println("容器集群部署配置创建成功: ${result.getString("id")}")
    }
    .onFailure { cause ->
        println("容器集群部署配置创建失败: ${cause.message}")
    }
```

### 3. 执行部署

现在，我们可以执行部署：

```kotlin
// 执行部署
deployManager.executeDeploy("YOUR_DEPLOY_ID")
    .onSuccess { result ->
        println("容器集群部署成功: $result")
        
        // 获取部署状态
        val statusId = result.getString("statusId")
        deployManager.getDeployStatus(statusId)
            .onSuccess { status ->
                println("部署状态: $status")
            }
            .onFailure { cause ->
                println("获取部署状态失败: ${cause.message}")
            }
    }
    .onFailure { cause ->
        println("容器集群部署失败: ${cause.message}")
    }
```

### 4. 查看部署状态

我们可以随时查看部署状态：

```kotlin
// 获取所有部署状态
deployManager.getAllDeployStatus()
    .onSuccess { statusList ->
        println("所有部署状态: $statusList")
    }
    .onFailure { cause ->
        println("获取所有部署状态失败: ${cause.message}")
    }
```

### 5. 部署 APIX 到集群

集群创建成功后，我们可以使用 kubectl 部署 APIX 到集群：

```bash
# 更新 kubeconfig
aws eks update-kubeconfig --name apix-cluster --region us-east-1

# 部署 APIX
kubectl apply -k k8s/kustomize/overlays/prod
```

## 部署架构

APIX 容器集群部署架构如下：

```
+------------------+     +------------------+     +------------------+
|                  |     |                  |     |                  |
|  Load Balancer   |---->|  APIX Gateway    |---->|  Backend Services|
|  (AWS ALB/NLB)   |     |  (Kubernetes)    |     |  (Kubernetes)    |
|                  |     |                  |     |                  |
+------------------+     +------------------+     +------------------+
                                 |
                                 |
                                 v
                         +------------------+
                         |                  |
                         |  Persistent      |
                         |  Storage (EBS)   |
                         |                  |
                         +------------------+
```

## 集群配置说明

### EKS 集群配置

| 参数 | 说明 | 默认值 |
|------|------|--------|
| name | 集群名称 | apix-cluster |
| version | Kubernetes 版本 | 1.27 |
| nodeCount | 节点数量 | 3 |
| nodeType | 节点类型 | t3.medium |

### 网络配置

| 参数 | 说明 | 默认值 |
|------|------|--------|
| vpcConfig.subnetIds | 子网 ID 列表 | - |
| vpcConfig.securityGroupIds | 安全组 ID 列表 | - |

### 日志配置

| 参数 | 说明 | 默认值 |
|------|------|--------|
| logging.clusterLogging | 集群日志类型 | api, audit, authenticator, controllerManager, scheduler |

## 性能优化

为了优化容器集群部署的性能，可以考虑以下几点：

1. **节点类型**：选择合适的节点类型，根据负载需求调整 CPU 和内存
2. **自动扩缩容**：配置 Kubernetes Horizontal Pod Autoscaler (HPA) 和 Cluster Autoscaler
3. **资源请求和限制**：为 Pod 设置合适的资源请求和限制
4. **亲和性和反亲和性**：使用节点亲和性和 Pod 反亲和性优化部署
5. **网络优化**：使用 AWS CNI 插件优化网络性能
6. **存储优化**：使用 EBS gp3 卷提高存储性能

## 故障排除

如果部署失败，可以尝试以下步骤：

1. 检查 AWS 凭证是否正确
2. 检查 IAM 权限是否足够
3. 查看 CloudWatch 日志
4. 检查部署状态和错误消息
5. 使用 eksctl 手动创建集群

## 参考资料

- [Amazon EKS 文档](https://docs.aws.amazon.com/eks/)
- [Kubernetes 文档](https://kubernetes.io/docs/)
- [APIX 文档](https://github.com/louloulinlv/apix/docs)
