#!/bin/bash

# 10 万并发优化启动脚本

echo "===== 启动 10 万并发优化版 APIX Gateway ====="

# 构建应用程序
echo "构建应用程序..."
./gradlew build -x test

# 设置系统参数
echo "设置系统参数..."
ulimit -n 500000 || echo "警告: 无法设置文件描述符限制，可能需要 root 权限"

# 设置操作系统参数（需要 root 权限）
if [ "$(uname)" == "Linux" ]; then
  echo "尝试设置 Linux 内核参数..."
  sudo sysctl -w net.core.somaxconn=65535 || echo "警告: 无法设置 net.core.somaxconn"
  sudo sysctl -w net.ipv4.tcp_max_syn_backlog=65535 || echo "警告: 无法设置 net.ipv4.tcp_max_syn_backlog"
  sudo sysctl -w net.ipv4.ip_local_port_range="1024 65535" || echo "警告: 无法设置 net.ipv4.ip_local_port_range"
  sudo sysctl -w net.ipv4.tcp_tw_reuse=1 || echo "警告: 无法设置 net.ipv4.tcp_tw_reuse"
  sudo sysctl -w net.ipv4.tcp_fin_timeout=15 || echo "警告: 无法设置 net.ipv4.tcp_fin_timeout"
  sudo sysctl -w net.core.netdev_max_backlog=65535 || echo "警告: 无法设置 net.core.netdev_max_backlog"
fi

# 设置 JVM 参数 - 针对 10 万并发优化
JVM_OPTS="-server"

# 内存设置 - 优化版
JVM_OPTS="$JVM_OPTS -Xms4g -Xmx12g"                               # 堆内存大小
JVM_OPTS="$JVM_OPTS -XX:MetaspaceSize=256m"                       # 元空间初始大小
JVM_OPTS="$JVM_OPTS -XX:MaxMetaspaceSize=512m"                    # 元空间最大大小
JVM_OPTS="$JVM_OPTS -XX:+AlwaysPreTouch"                          # 预分配内存
JVM_OPTS="$JVM_OPTS -XX:+UseCompressedOops"                       # 使用压缩对象指针

# 垃圾回收设置 - 优化版
JVM_OPTS="$JVM_OPTS -XX:+UseG1GC"                                 # 使用 G1 垃圾收集器
JVM_OPTS="$JVM_OPTS -XX:MaxGCPauseMillis=100"                     # 最大 GC 暂停时间
JVM_OPTS="$JVM_OPTS -XX:G1HeapRegionSize=8m"                      # G1 区域大小
JVM_OPTS="$JVM_OPTS -XX:InitiatingHeapOccupancyPercent=45"        # 启动并发 GC 的堆占用率
JVM_OPTS="$JVM_OPTS -XX:ConcGCThreads=16"                         # 并发 GC 线程数
JVM_OPTS="$JVM_OPTS -XX:ParallelGCThreads=16"                     # 并行 GC 线程数
JVM_OPTS="$JVM_OPTS -XX:+DisableExplicitGC"                       # 禁用显式 GC
JVM_OPTS="$JVM_OPTS -XX:+ExplicitGCInvokesConcurrent"             # 显式 GC 触发并发 GC
JVM_OPTS="$JVM_OPTS -XX:+ParallelRefProcEnabled"                  # 并行引用处理

# 网络设置
JVM_OPTS="$JVM_OPTS -Djava.net.preferIPv4Stack=true"              # 优先使用 IPv4
JVM_OPTS="$JVM_OPTS -Dsun.net.inetaddr.ttl=60"                    # DNS 缓存 TTL

# Netty 优化 - 针对高并发连接
JVM_OPTS="$JVM_OPTS -Dio.netty.leakDetection.level=disabled"      # 禁用内存泄漏检测
JVM_OPTS="$JVM_OPTS -Dio.netty.allocator.numHeapArenas=24"        # 堆内存区域数量
JVM_OPTS="$JVM_OPTS -Dio.netty.allocator.numDirectArenas=24"      # 直接内存区域数量
JVM_OPTS="$JVM_OPTS -Dio.netty.allocator.maxOrder=11"             # 最大分配大小
JVM_OPTS="$JVM_OPTS -Dio.netty.allocator.pageSize=8192"           # 页大小
JVM_OPTS="$JVM_OPTS -Dio.netty.allocator.maxCachedBufferCapacity=65536" # 最大缓存容量
JVM_OPTS="$JVM_OPTS -Dio.netty.allocator.tinyCacheSize=512"       # 小缓存大小
JVM_OPTS="$JVM_OPTS -Dio.netty.allocator.smallCacheSize=256"      # 小缓存大小
JVM_OPTS="$JVM_OPTS -Dio.netty.allocator.normalCacheSize=128"     # 普通缓存大小
JVM_OPTS="$JVM_OPTS -Dio.netty.noPreferDirect=true"               # 不优先使用直接内存
JVM_OPTS="$JVM_OPTS -Dio.netty.noUnsafe=false"                    # 使用 Unsafe 类
JVM_OPTS="$JVM_OPTS -Dio.netty.recycler.maxCapacityPerThread=4096" # 每个线程的最大容量

# Vert.x 优化 - 针对高并发连接
JVM_OPTS="$JVM_OPTS -Dvertx.maxEventLoopExecuteTime=10000000000"  # 最大事件循环执行时间（10秒）
JVM_OPTS="$JVM_OPTS -Dvertx.maxWorkerExecuteTime=120000000000"    # 最大工作线程执行时间（120秒）
JVM_OPTS="$JVM_OPTS -Dvertx.disableMetrics=false"                 # 启用 Vert.x 指标
JVM_OPTS="$JVM_OPTS -Dvertx.preferNativeTransport=true"           # 使用本地传输
JVM_OPTS="$JVM_OPTS -Dvertx.disableTCCL=true"                     # 禁用线程上下文类加载器
JVM_OPTS="$JVM_OPTS -Dvertx.threadChecks=false"                   # 禁用线程检查
JVM_OPTS="$JVM_OPTS -Dvertx.disableContextTimings=true"           # 禁用上下文计时
JVM_OPTS="$JVM_OPTS -Dvertx.disableHttpHeadersValidation=true"    # 禁用 HTTP 头验证
JVM_OPTS="$JVM_OPTS -Dvertx.eventLoopPoolSize=32"                 # 事件循环线程池大小
JVM_OPTS="$JVM_OPTS -Dvertx.workerPoolSize=128"                   # 工作线程池大小

# 配置文件路径
JVM_OPTS="$JVM_OPTS -Dapix.config.path=config/apix.json"          # 应用配置路径
JVM_OPTS="$JVM_OPTS -Dapix.vertx.config.path=src/main/resources/vertx-100k.json" # Vert.x 配置路径

# 调试和监控设置
JVM_OPTS="$JVM_OPTS -XX:+HeapDumpOnOutOfMemoryError"              # 内存溢出时生成堆转储
JVM_OPTS="$JVM_OPTS -XX:HeapDumpPath=./heapdump.hprof"            # 堆转储路径

# 显示 JVM 参数
echo "JVM 参数: $JVM_OPTS"

# 运行应用程序
echo "启动应用程序..."
java $JVM_OPTS -jar build/libs/apix-1.0.0-fat.jar
