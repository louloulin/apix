# APIX 网关性能优化计划 (perf3.md)

## 当前性能状况

- **当前性能**: 每秒42,852个请求（1,000个并发用户）
- **成功率**: 99.93%
- **平均响应时间**: 12.19毫秒
- **P95响应时间**: 31.47毫秒
- **目标性能**: 每秒100,000-200,000个请求

## 性能分析

通过对代码库和性能测试结果的分析，我们发现以下几个关键优化点：

1. **EventBus性能**: 已有JCToolsEventBus实现但未完全启用
2. **HTTP服务器配置**: 当前配置可能不是最优的
3. **线程池和事件循环**: 需要根据实际负载调整
4. **内存管理**: 当前内存配置过大，实际使用量很低
5. **网络参数**: 可以进一步优化TCP和HTTP/2参数

## 优化计划

### 1. EventBus优化 [高优先级]

EventBus是Vert.x的核心组件，优化它将显著提高整体性能。

#### 1.1 启用JCToolsEventBus

- [x] 在`Main.kt`中取消注释JCToolsEventBus初始化代码
- [x] 确保所有EventBus通信都通过JCToolsEventBus进行
- [x] 优化JCToolsEventBus的队列容量和处理策略

```kotlin
// 初始化JCToolsEventBus
val jcToolsEventBus = JCToolsEventBus.getInstance(vertx)
logger.info("JCTools EventBus initialized")
```

#### 1.2 优化BatchMessageProcessor

- [x] 增加默认批处理大小（从100增加到500-1000）
- [x] 减少批处理超时时间（从50ms减少到10-20ms）
- [x] 实现更智能的自适应批处理大小算法

```kotlin
// 批处理配置
private val batchSize = 1000               // 增加批处理大小
private val batchTimeoutMs = 20L           // 减少超时时间
```

#### 1.3 实现消息分区和并行处理

- [ ] 基于地址哈希实现消息分区
- [ ] 为每个分区创建独立的处理队列
- [ ] 实现工作窃取算法平衡分区间负载

### 2. HTTP服务器优化 [高优先级]

#### 2.1 优化HTTP服务器配置

- [x] 调整HTTP服务器参数以支持更高并发
- [x] 为性能测试禁用不必要的功能（如压缩）
- [x] 优化HTTP/2设置

```kotlin
// 优化HTTP服务器选项
val serverOptions = HttpServerOptions()
    .setTcpNoDelay(true)
    .setTcpFastOpen(true)
    .setTcpQuickAck(true)
    .setReusePort(true)
    .setAcceptBacklog(100000)
    .setIdleTimeout(300)
    // 高并发场景下禁用压缩
    .setCompressionSupported(false)
    .setDecompressionSupported(false)
```

#### 2.2 简化请求处理路径

- [x] 为性能测试创建专用的轻量级路由处理器
- [x] 减少中间件数量
- [x] 优化路由匹配算法

```kotlin
// 添加超轻量级端点，绕过常规中间件
mainRouter.route("/bench").handler { ctx ->
    ctx.response().end("OK")
}
```

#### 2.3 优化HTTP/2设置

- [x] 增加最大并发流数（从10,000增加到100,000）
- [x] 优化初始窗口大小和帧大小
- [x] 调整HPACK头部压缩参数

```kotlin
// 优化HTTP/2设置
val http2Settings = Http2Settings()
    .setMaxConcurrentStreams(100000)
    .setInitialWindowSize(2097152)  // 增加到2MB
    .setHeaderTableSize(16384)
    .setMaxHeaderListSize(65536)
    .setMaxFrameSize(24576)
    .setPushEnabled(false)
```

### 3. 线程池和事件循环优化 [中优先级]

#### 3.1 优化事件循环线程数

- [ ] 调整事件循环线程数（从每核心4个增加到6-8个）
- [ ] 实现事件循环亲和性以提高CPU缓存命中率
- [ ] 监控和平衡事件循环负载

```kotlin
// 优化事件循环线程数
val eventLoopPoolSize = availableProcessors * 6  // 每核心6个事件循环线程
```

#### 3.2 优化工作线程池

- [ ] 调整工作线程池大小（从每核心16个减少到8-12个）
- [ ] 实现优先级工作队列
- [ ] 优化工作线程调度策略

```kotlin
// 优化工作线程池大小
val workerPoolSize = availableProcessors * 10  // 每核心10个工作线程
```

#### 3.3 实现自适应线程池

- [ ] 实现动态调整线程池大小的机制
- [ ] 基于系统负载和响应时间自动调整
- [ ] 添加线程池性能监控和报告

### 4. 内存优化 [中优先级]

#### 4.1 优化JVM内存设置

- [ ] 调整堆内存大小（从8GB减少到2-4GB）
- [ ] 优化新生代和老年代比例
- [ ] 考虑使用ZGC替代G1GC以获得更低的延迟

```bash
# 优化JVM内存设置
JVM_OPTS="$JVM_OPTS -Xms4g -Xmx4g"  # 减少堆内存大小
JVM_OPTS="$JVM_OPTS -XX:+UseZGC"    # 使用ZGC替代G1GC
```

#### 4.2 实现和优化对象池

- [ ] 为频繁创建的对象实现对象池
- [ ] 优化MessageObjectPool的容量和策略
- [ ] 实现分层对象池以减少锁竞争

```kotlin
// 优化对象池配置
val messagePoolSize = 100000  // 增加对象池大小
val bufferPoolSize = 50000    // 增加缓冲区池大小
```

#### 4.3 减少内存分配和GC压力

- [ ] 优化字符串处理以减少临时对象
- [ ] 使用值类型和内联类减少装箱/拆箱
- [ ] 实现零拷贝数据处理

### 5. 网络优化 [中优先级]

#### 5.1 优化TCP参数

- [ ] 增加TCP缓冲区大小
- [ ] 优化TCP连接复用
- [ ] 调整TCP保活设置

```kotlin
// 优化TCP参数
.setReceiveBufferSize(262144)   // 增加接收缓冲区大小到256KB
.setSendBufferSize(262144)      // 增加发送缓冲区大小到256KB
```

#### 5.2 实现连接预热

- [ ] 实现连接预热机制
- [ ] 优化连接建立过程
- [ ] 实现连接池监控和管理

```kotlin
// 实现连接预热
val connectionWarmer = ConnectionWarmer.getInstance(vertx)
connectionWarmer.warmupConnections(100)  // 预热100个连接
```

#### 5.3 优化网络IO

- [ ] 实现零拷贝数据传输
- [ ] 优化缓冲区管理
- [ ] 实现批量网络IO

### 6. 系统级优化 [低优先级]

#### 6.1 操作系统参数调整

- [ ] 确保系统参数设置生效
- [ ] 增加文件描述符限制
- [ ] 优化网络栈参数

```bash
# 增加文件描述符限制
ulimit -n 1000000

# 优化网络栈参数
sysctl -w net.core.somaxconn=100000
sysctl -w net.ipv4.tcp_max_syn_backlog=100000
```

#### 6.2 JVM参数优化

- [ ] 优化JIT编译器设置
- [ ] 调整GC参数
- [ ] 启用实验性JVM优化

```bash
# 优化JIT编译器设置
JVM_OPTS="$JVM_OPTS -XX:+TieredCompilation"
JVM_OPTS="$JVM_OPTS -XX:+UseStringDeduplication"
```

#### 6.3 监控和性能分析

- [ ] 实现详细的性能监控
- [ ] 添加关键指标的实时报告
- [ ] 实现自动性能分析和瓶颈检测

## 实施计划

### 阶段1: EventBus和HTTP服务器优化 (1-2天) - 已完成

1. 启用JCToolsEventBus - 已完成
2. 优化BatchMessageProcessor - 已完成
3. 调整HTTP服务器配置 - 已完成
4. 简化请求处理路径 - 已完成
5. 优化HTTP/2设置 - 已完成

### 阶段2: 线程池和内存优化 (2-3天)

1. 优化事件循环和工作线程池
2. 调整JVM内存设置
3. 实现和优化对象池
4. 减少内存分配和GC压力

### 阶段3: 网络和系统级优化 (1-2天)

1. 优化TCP和HTTP/2参数 - 部分完成
2. 实现连接预热
3. 调整操作系统和JVM参数
4. 实现性能监控和报告

## 性能测试和验证

每个优化阶段完成后，使用以下测试验证性能改进：

1. **基准测试**: 使用k6-1k-test.js测试1,000并发用户
2. **优化端点测试**: 使用k6-bench-test.js测试优化后的/bench端点
3. **中等负载测试**: 使用k6-incremental-load.js测试1,000-10,000并发用户
4. **高负载测试**: 使用k6-100k-concurrent.js测试最高并发能力

## 预期结果

- **阶段1完成后**: 每秒60,000-80,000请求
- **阶段2完成后**: 每秒100,000-150,000请求
- **阶段3完成后**: 每秒150,000-200,000请求

## 风险和缓解措施

1. **内存泄漏风险**: 实现详细的内存监控和自动堆转储
2. **系统不稳定风险**: 逐步增加负载，监控系统稳定性
3. **优化冲突风险**: 每次只实施一组相关优化，避免相互干扰

## 结论

通过系统地实施上述优化计划，我们有信心将APIX网关的性能从当前的每秒42,852请求提升到目标的每秒100,000-200,000请求。优化将集中在EventBus、HTTP服务器配置、线程池、内存管理和网络参数等关键领域。
