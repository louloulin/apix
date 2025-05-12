# Jenkins 流水线模板示例

本文档提供了使用 APIX CI/CD 流水线管理器创建 Jenkins 流水线模板的示例，实现了 plan7.md 中的 4.2.1 节"CI/CD 流水线"功能。

## 概述

APIX 支持创建和管理 CI/CD 流水线模板，可以用于快速创建标准化的流水线。本示例将演示如何创建一个 Jenkins 流水线模板，包括构建、测试、分析和部署阶段。

## 模板结构

Jenkins 流水线模板包含以下组件：

1. **基本信息**：模板名称、描述和类型
2. **阶段**：流水线的各个阶段，如构建、测试、分析和部署
3. **任务**：每个阶段包含的任务，如编译、单元测试、代码分析等

## 创建模板

### 1. 创建基本模板

首先，我们需要创建一个基本的 Jenkins 流水线模板：

```kotlin
// 创建 Jenkins 流水线模板
val template = JsonObject()
    .put("name", "Java Maven Pipeline")
    .put("description", "标准 Java Maven 项目流水线")
    .put("type", "JENKINS")
    .put("stages", JsonArray())

// 创建模板
val pipelineManager = PipelineManager.getInstance(vertx)
pipelineManager.createTemplate(template)
    .onSuccess { result ->
        println("流水线模板创建成功: ${result.getString("id")}")
    }
    .onFailure { cause ->
        println("流水线模板创建失败: ${cause.message}")
    }
```

### 2. 添加构建阶段

接下来，我们添加构建阶段：

```kotlin
// 创建构建阶段
val buildStage = JsonObject()
    .put("name", "构建")
    .put("order", 1)
    .put("tasks", JsonArray()
        .add(JsonObject()
            .put("name", "Maven 编译")
            .put("type", "shell")
            .put("script", "mvn clean compile")
            .put("order", 1)
        )
        .add(JsonObject()
            .put("name", "Maven 打包")
            .put("type", "shell")
            .put("script", "mvn package -DskipTests")
            .put("order", 2)
        )
    )

// 更新模板
val updatedTemplate = template.copy()
updatedTemplate.getJsonArray("stages").add(buildStage)

pipelineManager.updateTemplate(templateId, updatedTemplate)
    .onSuccess { result ->
        println("流水线模板更新成功")
    }
    .onFailure { cause ->
        println("流水线模板更新失败: ${cause.message}")
    }
```

### 3. 添加测试阶段

然后，我们添加测试阶段：

```kotlin
// 创建测试阶段
val testStage = JsonObject()
    .put("name", "测试")
    .put("order", 2)
    .put("tasks", JsonArray()
        .add(JsonObject()
            .put("name", "单元测试")
            .put("type", "shell")
            .put("script", "mvn test")
            .put("order", 1)
        )
        .add(JsonObject()
            .put("name", "集成测试")
            .put("type", "shell")
            .put("script", "mvn verify -DskipUnitTests")
            .put("order", 2)
        )
    )

// 更新模板
updatedTemplate.getJsonArray("stages").add(testStage)

pipelineManager.updateTemplate(templateId, updatedTemplate)
    .onSuccess { result ->
        println("流水线模板更新成功")
    }
    .onFailure { cause ->
        println("流水线模板更新失败: ${cause.message}")
    }
```

### 4. 添加代码分析阶段

接下来，我们添加代码分析阶段：

```kotlin
// 创建代码分析阶段
val analyzeStage = JsonObject()
    .put("name", "代码分析")
    .put("order", 3)
    .put("tasks", JsonArray()
        .add(JsonObject()
            .put("name", "SonarQube 分析")
            .put("type", "shell")
            .put("script", "mvn sonar:sonar -Dsonar.host.url=\${SONAR_URL} -Dsonar.login=\${SONAR_TOKEN}")
            .put("order", 1)
        )
    )

// 更新模板
updatedTemplate.getJsonArray("stages").add(analyzeStage)

pipelineManager.updateTemplate(templateId, updatedTemplate)
    .onSuccess { result ->
        println("流水线模板更新成功")
    }
    .onFailure { cause ->
        println("流水线模板更新失败: ${cause.message}")
    }
```

### 5. 添加部署阶段

最后，我们添加部署阶段：

```kotlin
// 创建部署阶段
val deployStage = JsonObject()
    .put("name", "部署")
    .put("order", 4)
    .put("tasks", JsonArray()
        .add(JsonObject()
            .put("name", "部署到测试环境")
            .put("type", "shell")
            .put("script", """
                |if [ "\${ENVIRONMENT}" = "test" ]; then
                |    echo "部署到测试环境"
                |    scp target/*.jar user@test-server:/opt/app/
                |    ssh user@test-server "cd /opt/app && ./restart.sh"
                |fi
            """.trimMargin())
            .put("order", 1)
        )
        .add(JsonObject()
            .put("name", "部署到生产环境")
            .put("type", "shell")
            .put("script", """
                |if [ "\${ENVIRONMENT}" = "prod" ]; then
                |    echo "部署到生产环境"
                |    scp target/*.jar user@prod-server:/opt/app/
                |    ssh user@prod-server "cd /opt/app && ./restart.sh"
                |fi
            """.trimMargin())
            .put("order", 2)
        )
    )

// 更新模板
updatedTemplate.getJsonArray("stages").add(deployStage)

pipelineManager.updateTemplate(templateId, updatedTemplate)
    .onSuccess { result ->
        println("流水线模板更新成功")
    }
    .onFailure { cause ->
        println("流水线模板更新失败: ${cause.message}")
    }
```

## 使用模板创建流水线

现在，我们可以使用模板创建流水线：

```kotlin
// 创建流水线配置
val pipelineConfig = JsonObject()
    .put("name", "我的 Java 项目")
    .put("description", "我的 Java Maven 项目流水线")
    .put("type", "JENKINS")
    .put("jenkinsUrl", "http://jenkins.example.com")
    .put("username", "admin")
    .put("apiToken", "YOUR_API_TOKEN")
    .put("templateId", templateId)

// 创建流水线
pipelineManager.createPipeline(pipelineConfig)
    .onSuccess { result ->
        println("流水线创建成功: ${result.getString("id")}")
    }
    .onFailure { cause ->
        println("流水线创建失败: ${cause.message}")
    }
```

## 执行流水线

创建流水线后，我们可以执行流水线：

```kotlin
// 执行参数
val params = JsonObject()
    .put("BRANCH", "main")
    .put("ENVIRONMENT", "test")
    .put("SONAR_URL", "http://sonar.example.com")
    .put("SONAR_TOKEN", "YOUR_SONAR_TOKEN")

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

## 生成 Jenkinsfile

我们可以从流水线配置生成 Jenkinsfile：

```kotlin
// 获取流水线实例
val pipeline = pipelineFactory.getPipeline(pipelineId) as JenkinsPipeline

// 生成 Jenkinsfile
pipeline.generateJenkinsfile()
    .onSuccess { jenkinsfile ->
        println("Jenkinsfile 生成成功:")
        println(jenkinsfile)
    }
    .onFailure { cause ->
        println("Jenkinsfile 生成失败: ${cause.message}")
    }
```

生成的 Jenkinsfile 示例：

```groovy
pipeline {
    agent any

    tools {
        maven 'Maven 3.8.6'
        jdk 'JDK 17'
    }

    options {
        timeout(time: 1, unit: 'HOURS')
        disableConcurrentBuilds()
    }

    stages {
        stage('构建') {
            steps {
                sh '''
                    mvn clean compile
                '''
                sh '''
                    mvn package -DskipTests
                '''
            }
        }
        stage('测试') {
            steps {
                sh '''
                    mvn test
                '''
                sh '''
                    mvn verify -DskipUnitTests
                '''
            }
        }
        stage('代码分析') {
            steps {
                sh '''
                    mvn sonar:sonar -Dsonar.host.url=${SONAR_URL} -Dsonar.login=${SONAR_TOKEN}
                '''
            }
        }
        stage('部署') {
            steps {
                sh '''
                    if [ "${ENVIRONMENT}" = "test" ]; then
                        echo "部署到测试环境"
                        scp target/*.jar user@test-server:/opt/app/
                        ssh user@test-server "cd /opt/app && ./restart.sh"
                    fi
                '''
                sh '''
                    if [ "${ENVIRONMENT}" = "prod" ]; then
                        echo "部署到生产环境"
                        scp target/*.jar user@prod-server:/opt/app/
                        ssh user@prod-server "cd /opt/app && ./restart.sh"
                    fi
                '''
            }
        }
    }

    post {
        success {
            echo 'Pipeline executed successfully'
        }
        failure {
            echo 'Pipeline execution failed'
        }
        always {
            echo 'Pipeline execution completed'
        }
    }
}
```

## 模板参数

流水线模板支持以下参数：

| 参数 | 说明 | 默认值 |
|------|------|--------|
| name | 模板名称 | - |
| description | 模板描述 | - |
| type | 模板类型 | - |
| stages | 阶段列表 | [] |

### 阶段参数

| 参数 | 说明 | 默认值 |
|------|------|--------|
| name | 阶段名称 | - |
| order | 阶段顺序 | - |
| tasks | 任务列表 | [] |

### 任务参数

| 参数 | 说明 | 默认值 |
|------|------|--------|
| name | 任务名称 | - |
| type | 任务类型 (shell, bat, groovy) | - |
| script | 任务脚本 | - |
| order | 任务顺序 | - |

## 最佳实践

1. **模板化**：为不同类型的项目创建标准化的流水线模板
2. **参数化**：使用参数化脚本，增加流水线的灵活性
3. **阶段划分**：合理划分流水线阶段，使流水线结构清晰
4. **任务顺序**：合理安排任务顺序，确保依赖关系正确
5. **错误处理**：添加错误处理和回滚机制，确保流水线可靠性
6. **通知机制**：添加通知机制，及时获取流水线执行状态

## 参考资料

- [Jenkins Pipeline 文档](https://www.jenkins.io/doc/book/pipeline/)
- [Jenkins Shared Libraries](https://www.jenkins.io/doc/book/pipeline/shared-libraries/)
- [APIX 文档](https://github.com/louloulinlv/apix/docs)
