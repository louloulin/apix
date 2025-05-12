# 自动测试示例

本文档提供了使用 APIX CI/CD 流水线管理器实现自动测试的示例，实现了 plan7.md 中的 4.2.1 节"CI/CD 流水线"功能。

## 概述

APIX 支持在 CI/CD 流水线中集成各种自动测试，包括单元测试、集成测试和性能测试。本示例将演示如何在流水线中配置和执行自动测试。

## 测试类型

APIX 支持以下类型的自动测试：

1. **单元测试**：测试单个组件或函数的功能
2. **集成测试**：测试多个组件之间的交互
3. **性能测试**：测试系统的性能和负载能力
4. **API 测试**：测试 API 的功能和性能
5. **UI 测试**：测试用户界面的功能和交互

## 单元测试配置

### 1. 创建单元测试任务

首先，我们需要在流水线中创建单元测试任务：

```kotlin
// 创建单元测试任务
val unitTestTask = JsonObject()
    .put("name", "单元测试")
    .put("type", "shell")
    .put("script", """
        |echo "执行单元测试"
        |mvn test
        |
        |# 生成测试报告
        |mvn surefire-report:report
    """.trimMargin())
    .put("order", 1)

// 添加到测试阶段
val testStage = pipeline.getStageDetails("test").result()
pipeline.addTask("test", unitTestTask)
    .onSuccess { result ->
        println("单元测试任务添加成功: ${result.getString("id")}")
    }
    .onFailure { cause ->
        println("单元测试任务添加失败: ${cause.message}")
    }
```

### 2. 配置测试覆盖率

我们可以添加测试覆盖率分析：

```kotlin
// 创建测试覆盖率任务
val coverageTask = JsonObject()
    .put("name", "测试覆盖率")
    .put("type", "shell")
    .put("script", """
        |echo "执行测试覆盖率分析"
        |mvn jacoco:report
        |
        |# 检查测试覆盖率
        |COVERAGE=$(grep -A 1 "Total" target/site/jacoco/index.html | grep -o "[0-9][0-9].[0-9][0-9]%" | head -1 | cut -d'%' -f1)
        |echo "测试覆盖率: $COVERAGE%"
        |
        |if (( $(echo "$COVERAGE < 80" | bc -l) )); then
        |    echo "测试覆盖率低于 80%"
        |    exit 1
        |fi
    """.trimMargin())
    .put("order", 2)

// 添加到测试阶段
pipeline.addTask("test", coverageTask)
    .onSuccess { result ->
        println("测试覆盖率任务添加成功: ${result.getString("id")}")
    }
    .onFailure { cause ->
        println("测试覆盖率任务添加失败: ${cause.message}")
    }
```

## 集成测试配置

### 1. 创建集成测试任务

接下来，我们创建集成测试任务：

```kotlin
// 创建集成测试任务
val integrationTestTask = JsonObject()
    .put("name", "集成测试")
    .put("type", "shell")
    .put("script", """
        |echo "执行集成测试"
        |
        |# 启动测试环境
        |docker-compose -f docker-compose.test.yml up -d
        |
        |# 等待服务启动
        |sleep 10
        |
        |# 执行集成测试
        |mvn verify -DskipUnitTests
        |
        |# 停止测试环境
        |docker-compose -f docker-compose.test.yml down
    """.trimMargin())
    .put("order", 1)

// 创建集成测试阶段
val integrationStage = JsonObject()
    .put("name", "集成测试")
    .put("order", 3)

// 添加集成测试阶段
pipeline.addStage(integrationStage)
    .compose { stage ->
        // 添加集成测试任务
        pipeline.addTask(stage.getString("id"), integrationTestTask)
    }
    .onSuccess { result ->
        println("集成测试任务添加成功: ${result.getString("id")}")
    }
    .onFailure { cause ->
        println("集成测试任务添加失败: ${cause.message}")
    }
```

## 性能测试配置

### 1. 创建性能测试任务

然后，我们创建性能测试任务：

```kotlin
// 创建性能测试任务
val performanceTestTask = JsonObject()
    .put("name", "性能测试")
    .put("type", "shell")
    .put("script", """
        |echo "执行性能测试"
        |
        |# 启动测试环境
        |docker-compose -f docker-compose.perf.yml up -d
        |
        |# 等待服务启动
        |sleep 10
        |
        |# 执行 K6 性能测试
        |k6 run --out json=results.json performance-test.js
        |
        |# 分析测试结果
        |cat results.json | jq '.metrics.http_req_duration.avg'
        |
        |# 检查性能指标
        |AVG_RESPONSE_TIME=$(cat results.json | jq '.metrics.http_req_duration.avg')
        |echo "平均响应时间: $AVG_RESPONSE_TIME ms"
        |
        |if (( $(echo "$AVG_RESPONSE_TIME > 100" | bc -l) )); then
        |    echo "平均响应时间超过 100ms"
        |    exit 1
        |fi
        |
        |# 停止测试环境
        |docker-compose -f docker-compose.perf.yml down
    """.trimMargin())
    .put("order", 1)

// 创建性能测试阶段
val performanceStage = JsonObject()
    .put("name", "性能测试")
    .put("order", 4)

// 添加性能测试阶段
pipeline.addStage(performanceStage)
    .compose { stage ->
        // 添加性能测试任务
        pipeline.addTask(stage.getString("id"), performanceTestTask)
    }
    .onSuccess { result ->
        println("性能测试任务添加成功: ${result.getString("id")}")
    }
    .onFailure { cause ->
        println("性能测试任务添加失败: ${cause.message}")
    }
```

## API 测试配置

### 1. 创建 API 测试任务

接下来，我们创建 API 测试任务：

```kotlin
// 创建 API 测试任务
val apiTestTask = JsonObject()
    .put("name", "API 测试")
    .put("type", "shell")
    .put("script", """
        |echo "执行 API 测试"
        |
        |# 启动测试环境
        |docker-compose -f docker-compose.api.yml up -d
        |
        |# 等待服务启动
        |sleep 10
        |
        |# 执行 Postman 测试
        |newman run api-tests.json -e test-env.json --reporters cli,junit --reporter-junit-export results/api-tests.xml
        |
        |# 检查测试结果
        |if [ $? -ne 0 ]; then
        |    echo "API 测试失败"
        |    exit 1
        |fi
        |
        |# 停止测试环境
        |docker-compose -f docker-compose.api.yml down
    """.trimMargin())
    .put("order", 1)

// 创建 API 测试阶段
val apiTestStage = JsonObject()
    .put("name", "API 测试")
    .put("order", 5)

// 添加 API 测试阶段
pipeline.addStage(apiTestStage)
    .compose { stage ->
        // 添加 API 测试任务
        pipeline.addTask(stage.getString("id"), apiTestTask)
    }
    .onSuccess { result ->
        println("API 测试任务添加成功: ${result.getString("id")}")
    }
    .onFailure { cause ->
        println("API 测试任务添加失败: ${cause.message}")
    }
```

## UI 测试配置

### 1. 创建 UI 测试任务

最后，我们创建 UI 测试任务：

```kotlin
// 创建 UI 测试任务
val uiTestTask = JsonObject()
    .put("name", "UI 测试")
    .put("type", "shell")
    .put("script", """
        |echo "执行 UI 测试"
        |
        |# 启动测试环境
        |docker-compose -f docker-compose.ui.yml up -d
        |
        |# 等待服务启动
        |sleep 10
        |
        |# 执行 Playwright 测试
        |npx playwright test
        |
        |# 检查测试结果
        |if [ $? -ne 0 ]; then
        |    echo "UI 测试失败"
        |    exit 1
        |fi
        |
        |# 停止测试环境
        |docker-compose -f docker-compose.ui.yml down
    """.trimMargin())
    .put("order", 1)

// 创建 UI 测试阶段
val uiTestStage = JsonObject()
    .put("name", "UI 测试")
    .put("order", 6)

// 添加 UI 测试阶段
pipeline.addStage(uiTestStage)
    .compose { stage ->
        // 添加 UI 测试任务
        pipeline.addTask(stage.getString("id"), uiTestTask)
    }
    .onSuccess { result ->
        println("UI 测试任务添加成功: ${result.getString("id")}")
    }
    .onFailure { cause ->
        println("UI 测试任务添加失败: ${cause.message}")
    }
```

## 测试报告

### 1. 创建测试报告任务

我们可以添加一个任务来收集和发布测试报告：

```kotlin
// 创建测试报告任务
val reportTask = JsonObject()
    .put("name", "测试报告")
    .put("type", "shell")
    .put("script", """
        |echo "生成测试报告"
        |
        |# 收集测试报告
        |mkdir -p reports
        |cp target/site/surefire-report.html reports/unit-test-report.html
        |cp target/site/jacoco/index.html reports/coverage-report.html
        |cp results/api-tests.xml reports/api-test-report.xml
        |cp playwright-report/index.html reports/ui-test-report.html
        |
        |# 发布测试报告
        |echo "发布测试报告到 Jenkins"
        |
        |# 发送通知
        |echo "发送测试报告通知"
    """.trimMargin())
    .put("order", 1)

// 创建报告阶段
val reportStage = JsonObject()
    .put("name", "测试报告")
    .put("order", 7)

// 添加报告阶段
pipeline.addStage(reportStage)
    .compose { stage ->
        // 添加报告任务
        pipeline.addTask(stage.getString("id"), reportTask)
    }
    .onSuccess { result ->
        println("测试报告任务添加成功: ${result.getString("id")}")
    }
    .onFailure { cause ->
        println("测试报告任务添加失败: ${cause.message}")
    }
```

## 执行测试

创建流水线后，我们可以执行流水线来运行自动测试：

```kotlin
// 执行参数
val params = JsonObject()
    .put("BRANCH", "main")
    .put("ENVIRONMENT", "test")

// 执行流水线
pipelineManager.executePipeline(pipelineId, params)
    .onSuccess { result ->
        println("流水线执行成功: ${result.getString("id")}")
        
        // 获取执行状态
        val executionId = result.getString("id")
        pipelineManager.getPipelineExecutionDetails(pipelineId, executionId)
            .onSuccess { details ->
                println("执行状态: ${details.getString("status")}")
            }
            .onFailure { cause ->
                println("获取执行状态失败: ${cause.message}")
            }
    }
    .onFailure { cause ->
        println("流水线执行失败: ${cause.message}")
    }
```

## 测试工具集成

APIX 支持集成以下测试工具：

### 单元测试工具

- JUnit (Java)
- TestNG (Java)
- pytest (Python)
- Jest (JavaScript)
- Mocha (JavaScript)
- NUnit (.NET)
- xUnit (.NET)

### 集成测试工具

- Spring Boot Test (Java)
- TestContainers (Java)
- pytest-integration (Python)
- Cypress (JavaScript)

### 性能测试工具

- JMeter
- K6
- Gatling
- Locust
- Apache Bench

### API 测试工具

- Postman/Newman
- REST Assured
- Karate
- SoapUI

### UI 测试工具

- Selenium
- Playwright
- Cypress
- Puppeteer
- Appium

## 最佳实践

1. **测试金字塔**：遵循测试金字塔原则，编写更多的单元测试，适量的集成测试和少量的 UI 测试
2. **测试隔离**：确保测试之间相互隔离，不互相影响
3. **测试环境**：使用容器技术创建隔离的测试环境
4. **测试数据**：使用测试数据生成器创建测试数据
5. **测试覆盖率**：监控测试覆盖率，确保代码的充分测试
6. **测试报告**：生成详细的测试报告，方便问题定位
7. **测试自动化**：将测试完全自动化，减少人工干预
8. **测试并行化**：并行执行测试，提高测试效率

## 参考资料

- [JUnit 文档](https://junit.org/junit5/docs/current/user-guide/)
- [TestNG 文档](https://testng.org/doc/)
- [K6 文档](https://k6.io/docs/)
- [Postman 文档](https://learning.postman.com/docs/getting-started/introduction/)
- [Playwright 文档](https://playwright.dev/docs/intro)
- [APIX 文档](https://github.com/louloulinlv/apix/docs)
