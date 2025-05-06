# APIX测试指南

本文档提供了如何运行APIX测试的说明。

## 单元测试

单元测试用于测试各个组件的功能。

### 运行所有单元测试

```bash
./gradlew test
```

### 运行特定的单元测试

```bash
./gradlew test --tests "com.louloulin.apix.core.eventbus.JCToolsEventBusTest"
```

## 集成测试

集成测试用于测试所有组件的协同工作。

### 运行集成测试

```bash
./gradlew test --tests "com.louloulin.apix.core.integration.IntegrationTest"
```

## 性能测试

性能测试用于测试系统在高负载下的性能。

### 运行性能测试

```bash
./gradlew test --tests "com.louloulin.apix.core.performance.PerformanceTest"
```

## 稳定性测试

稳定性测试用于测试系统在长时间运行下的稳定性。

### 运行稳定性测试

```bash
./gradlew test --tests "com.louloulin.apix.core.stability.StabilityTest"
```

## 负载测试

负载测试用于测试系统在高负载下的性能，使用k6工具。

### 安装k6

```bash
# 使用Homebrew安装（macOS）
brew install k6

# 使用apt安装（Ubuntu/Debian）
sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6

# 使用Docker
docker pull loadimpact/k6
```

### 运行负载测试

```bash
# 直接运行
k6 run src/test/resources/k6/load-test.js

# 使用Docker运行
docker run -i loadimpact/k6 run - <src/test/resources/k6/load-test.js
```

### 负载测试选项

- 运行特定场景：

```bash
k6 run --scenario constant_load src/test/resources/k6/load-test.js
```

- 运行特定函数：

```bash
k6 run --fn smallData src/test/resources/k6/load-test.js
```

- 自定义虚拟用户数和持续时间：

```bash
k6 run --vus 100 --duration 30s src/test/resources/k6/load-test.js
```

## 测试报告

测试报告位于`build/reports/tests/test`目录下，可以通过浏览器查看。

```bash
open build/reports/tests/test/index.html
```

## 测试覆盖率

测试覆盖率报告位于`build/reports/jacoco/test/html`目录下，可以通过浏览器查看。

```bash
./gradlew jacocoTestReport
open build/reports/jacoco/test/html/index.html
```

## 持续集成

在持续集成环境中，可以使用以下命令运行所有测试：

```bash
./gradlew check
```

这将运行所有测试并生成测试报告和测试覆盖率报告。
