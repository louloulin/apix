# APIX Gateway Native Image 性能优化指南

## 简介

本文档介绍了如何将APIX Gateway编译为GraalVM native image，以及这种方式相比传统JVM模式的性能优势。Native image技术可以将Java应用程序预先编译成本地可执行文件，从而实现更快的启动时间、更低的内存占用和更稳定的性能表现。

## 为什么使用Native Image？

将APIX Gateway编译为native image有以下优势：

1. **更快的启动时间**：相比JVM模式，native image启动时间通常快10-100倍
2. **更低的内存占用**：native image不需要JVM运行时，内存占用显著降低
3. **更稳定的性能**：没有JVM预热和GC暂停，性能更加稳定
4. **更小的部署包**：可以创建自包含的可执行文件，简化部署
5. **更好的安全性**：减少攻击面，提高安全性

## 先决条件

要构建APIX Gateway的native image，您需要：

1. GraalVM 22.3+（推荐使用最新版本）
2. Native Image工具（`gu install native-image`）
3. 适当的构建工具（Maven或Gradle）
4. 足够的内存（至少8GB，推荐16GB）

## 构建Native Image

我们提供了两个脚本来构建和测试native image：

1. `build-native.sh`：构建native image并进行基本性能测试
2. `compare-performance.sh`：比较JVM模式和native模式的性能差异

### 使用build-native.sh

```bash
# 确保脚本有执行权限
chmod +x build-native.sh

# 运行构建脚本
./build-native.sh
```

这个脚本会：
- 检查GraalVM是否已安装
- 设置系统参数以支持高并发
- 构建native image
- 启动应用并运行性能测试
- 输出测试结果

### 使用compare-performance.sh

```bash
# 确保脚本有执行权限
chmod +x compare-performance.sh

# 运行比较脚本
./compare-performance.sh
```

这个脚本会：
- 构建JVM和native两种模式的应用
- 测量两种模式的启动时间
- 对两种模式进行相同的性能测试
- 生成比较报告

## 性能优化配置

为了获得最佳性能，我们对native image构建进行了以下优化：

### 1. 反射配置

在`src/main/resources/META-INF/native-image/reflect-config.json`中，我们配置了需要反射访问的类：

```json
[
  {
    "name": "com.louloulin.apix.core.eventbus.JCToolsEventBus",
    "allDeclaredConstructors": true,
    "allPublicConstructors": true,
    "allDeclaredMethods": true,
    "allPublicMethods": true
  },
  // 其他需要反射的类...
]
```

### 2. 资源配置

在`src/main/resources/META-INF/native-image/resource-config.json`中，我们配置了需要包含在native image中的资源：

```json
{
  "resources": {
    "includes": [
      {
        "pattern": ".*\\.json"
      },
      // 其他资源模式...
    ]
  }
}
```

### 3. Native Image构建参数

在`src/main/resources/META-INF/native-image/native-image.properties`中，我们配置了构建参数：

```properties
Args = --initialize-at-build-time=ch.qos,org.slf4j,org.jctools \
       --initialize-at-run-time=io.vertx.ext.web.client.WebClientOptions,io.netty.channel.epoll,io.netty.channel.unix,io.netty.handler.ssl \
       --no-fallback \
       // 其他参数...
```

## 性能测试

我们使用k6进行性能测试，测试脚本位于`k6-native-test.js`。这个脚本会：

1. 从100个并发用户开始
2. 逐步增加到10,000个并发用户
3. 保持高负载一段时间
4. 逐步减少负载
5. 收集详细的性能指标

## 性能优化建议

要进一步优化native image的性能，可以考虑：

1. **调整内存设置**：使用`--gc=G1`和适当的堆大小设置
2. **优化反射使用**：减少反射使用，或确保所有反射都在配置文件中
3. **预热关键路径**：实现应用启动时的预热逻辑
4. **使用直接内存**：对于高性能场景，使用直接内存而不是堆内存
5. **启用本地传输**：使用`-Dvertx.preferNativeTransport=true`启用本地传输

## 已知限制

使用native image时需要注意以下限制：

1. **动态类加载受限**：不支持运行时动态加载未知类
2. **反射需要配置**：所有反射使用都需要在配置文件中声明
3. **资源访问受限**：需要在配置中包含所有资源
4. **JNI使用需要特殊处理**：使用JNI需要额外配置
5. **某些JVM功能不可用**：如动态代理生成、某些JVMTI功能等

## 结论

将APIX Gateway编译为native image可以显著提高性能，特别是在启动时间、内存占用和性能稳定性方面。通过正确的配置和优化，可以充分发挥native image的优势，同时保持应用的功能完整性。

对于需要快速启动、低内存占用和稳定性能的场景，如Kubernetes环境、Serverless部署或边缘计算，native image是一个理想的选择。
