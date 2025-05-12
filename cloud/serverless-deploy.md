# Serverless 部署示例

本文档提供了使用 APIX 多云部署管理器进行 Serverless 部署的示例，实现了 plan7.md 中的 4.1.3 节"云原生部署"功能。

## 概述

APIX 支持将 API 网关部署到各大云提供商的 Serverless 平台，包括：

- AWS Lambda
- Azure Functions
- Google Cloud Functions
- 阿里云函数计算
- 腾讯云云函数
- 华为云函数工作流

本示例将演示如何将 APIX 部署到 AWS Lambda。

## 前提条件

- 已安装 APIX
- 已配置 AWS 账号和凭证
- 已安装 AWS CLI

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

### 2. 创建 Serverless 部署配置

接下来，我们需要创建一个 Serverless 部署配置：

```kotlin
// 创建 Serverless 部署配置
val deployConfig = JsonObject()
    .put("name", "APIX Serverless")
    .put("providerType", "AWS")
    .put("deployType", "serverless")
    .put("region", "us-east-1")
    .put("serviceConfig", JsonObject()
        .put("name", "apix-gateway")
        .put("runtime", "java11")
        .put("memory", 512)
        .put("timeout", 30)
        .put("handler", "com.louloulin.apix.lambda.Handler")
        .put("environment", JsonObject()
            .put("APIX_MODE", "serverless")
            .put("APIX_LOG_LEVEL", "info")
        )
    )

// 创建部署配置
deployManager.createDeployConfig(deployConfig)
    .onSuccess { result ->
        println("Serverless 部署配置创建成功: ${result.getString("id")}")
    }
    .onFailure { cause ->
        println("Serverless 部署配置创建失败: ${cause.message}")
    }
```

### 3. 执行部署

现在，我们可以执行部署：

```kotlin
// 执行部署
deployManager.executeDeploy("YOUR_DEPLOY_ID")
    .onSuccess { result ->
        println("Serverless 部署成功: $result")
        
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
        println("Serverless 部署失败: ${cause.message}")
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

## 部署架构

APIX Serverless 部署架构如下：

```
+------------------+     +------------------+     +------------------+
|                  |     |                  |     |                  |
|  API Gateway     |---->|  Lambda Function |---->|  DynamoDB        |
|  (AWS API GW)    |     |  (APIX Gateway)  |     |  (Config Store)  |
|                  |     |                  |     |                  |
+------------------+     +------------------+     +------------------+
                                 |
                                 |
                                 v
                         +------------------+
                         |                  |
                         |  S3 Bucket       |
                         |  (Plugin Store)  |
                         |                  |
                         +------------------+
```

## 配置说明

### Lambda 函数配置

| 参数 | 说明 | 默认值 |
|------|------|--------|
| name | 函数名称 | apix-gateway |
| runtime | 运行时 | java11 |
| memory | 内存大小 (MB) | 512 |
| timeout | 超时时间 (秒) | 30 |
| handler | 处理程序 | com.louloulin.apix.lambda.Handler |

### 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| APIX_MODE | APIX 运行模式 | serverless |
| APIX_LOG_LEVEL | 日志级别 | info |
| APIX_CONFIG_STORE | 配置存储类型 | dynamodb |
| APIX_CONFIG_TABLE | DynamoDB 表名 | apix-config |
| APIX_PLUGIN_STORE | 插件存储类型 | s3 |
| APIX_PLUGIN_BUCKET | S3 存储桶名 | apix-plugins |

## 性能优化

为了优化 Serverless 部署的性能，可以考虑以下几点：

1. **预热函数**：使用 AWS CloudWatch 定时触发函数，避免冷启动
2. **增加内存**：增加 Lambda 函数的内存分配，可以提高 CPU 性能
3. **使用 Provisioned Concurrency**：为函数配置预置并发，减少冷启动
4. **优化依赖**：减少依赖包大小，加快函数加载速度
5. **使用 Lambda Layers**：将公共依赖放在 Lambda Layers 中，减少函数包大小

## 故障排除

如果部署失败，可以尝试以下步骤：

1. 检查 AWS 凭证是否正确
2. 检查 IAM 权限是否足够
3. 查看 CloudWatch 日志
4. 检查部署状态和错误消息
5. 尝试手动部署 Lambda 函数

## 参考资料

- [AWS Lambda 文档](https://docs.aws.amazon.com/lambda/)
- [Serverless Framework](https://www.serverless.com/)
- [APIX 文档](https://github.com/louloulinlv/apix/docs)
