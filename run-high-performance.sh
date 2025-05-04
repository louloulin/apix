#!/bin/bash

# Vert.x 超高性能优化启动脚本
# 配置最大内存 30G 和其他性能优化参数

echo "===== 启动 Vert.x 超高性能优化版应用 ====="

# 设置系统参数
echo "设置系统参数..."
ulimit -n 1000000 || echo "警告: 无法设置文件描述符限制，可能需要 root 权限"

# 设置操作系统参数（需要 root 权限）
if [ "$(uname)" == "Linux" ]; then
  echo "尝试设置 Linux 内核参数..."
  sudo sysctl -w net.core.somaxconn=65535 || echo "警告: 无法设置 net.core.somaxconn"
  sudo sysctl -w net.ipv4.tcp_max_syn_backlog=65535 || echo "警告: 无法设置 net.ipv4.tcp_max_syn_backlog"
  sudo sysctl -w net.ipv4.ip_local_port_range="1024 65535" || echo "警告: 无法设置 net.ipv4.ip_local_port_range"
  sudo sysctl -w net.ipv4.tcp_tw_reuse=1 || echo "警告: 无法设置 net.ipv4.tcp_tw_reuse"
  sudo sysctl -w net.ipv4.tcp_fin_timeout=10 || echo "警告: 无法设置 net.ipv4.tcp_fin_timeout"
  sudo sysctl -w net.core.netdev_max_backlog=65535 || echo "警告: 无法设置 net.core.netdev_max_backlog"
  sudo sysctl -w net.ipv4.tcp_keepalive_time=600 || echo "警告: 无法设置 net.ipv4.tcp_keepalive_time"
  sudo sysctl -w net.ipv4.tcp_keepalive_intvl=60 || echo "警告: 无法设置 net.ipv4.tcp_keepalive_intvl"
  sudo sysctl -w net.ipv4.tcp_keepalive_probes=10 || echo "警告: 无法设置 net.ipv4.tcp_keepalive_probes"
  sudo sysctl -w net.core.rmem_max=16777216 || echo "警告: 无法设置 net.core.rmem_max"
  sudo sysctl -w net.core.wmem_max=16777216 || echo "警告: 无法设置 net.core.wmem_max"
  sudo sysctl -w net.ipv4.tcp_rmem="4096 87380 16777216" || echo "警告: 无法设置 net.ipv4.tcp_rmem"
  sudo sysctl -w net.ipv4.tcp_wmem="4096 65536 16777216" || echo "警告: 无法设置 net.ipv4.tcp_wmem"
  
  # 设置大页内存（如果支持）
  if [ -d /sys/kernel/mm/hugepages ]; then
    echo "设置大页内存..."
    sudo sysctl -w vm.nr_hugepages=2048 || echo "警告: 无法设置 vm.nr_hugepages"
  fi
  
  # 设置 NUMA 平衡（如果支持）
  if [ -f /proc/sys/kernel/numa_balancing ]; then
    echo "设置 NUMA 平衡..."
    sudo sysctl -w kernel.numa_balancing=0 || echo "警告: 无法设置 kernel.numa_balancing"
  fi
fi

# 设置 JVM 参数
JVM_OPTS="-server"

# 内存设置优化 - 最大内存 30G
JVM_OPTS="$JVM_OPTS -Xms8g -Xmx30g"                               # 堆内存大小
JVM_OPTS="$JVM_OPTS -XX:MetaspaceSize=512m"                       # 元空间初始大小
JVM_OPTS="$JVM_OPTS -XX:MaxMetaspaceSize=1g"                      # 元空间最大大小
JVM_OPTS="$JVM_OPTS -XX:+AlwaysPreTouch"                          # 预分配内存
JVM_OPTS="$JVM_OPTS -XX:+UseCompressedOops"                       # 使用压缩对象指针
JVM_OPTS="$JVM_OPTS -XX:MaxDirectMemorySize=8g"                   # 直接内存最大值

# 垃圾回收优化 - ZGC
JVM_OPTS="$JVM_OPTS -XX:+UseZGC"                                  # 使用 ZGC
JVM_OPTS="$JVM_OPTS -XX:+UnlockExperimentalVMOptions"             # 解锁实验性选项
JVM_OPTS="$JVM_OPTS -XX:ZCollectionInterval=10"                   # ZGC 收集间隔
JVM_OPTS="$JVM_OPTS -XX:ZAllocationSpikeTolerance=5"              # 分配尖峰容差
JVM_OPTS="$JVM_OPTS -XX:+ZUncommit"                               # 允许 ZGC 释放未使用的内存
JVM_OPTS="$JVM_OPTS -XX:ZUncommitDelay=300"                       # 释放内存前的延迟时间
JVM_OPTS="$JVM_OPTS -XX:+UseNUMA"                                 # 启用 NUMA 感知
JVM_OPTS="$JVM_OPTS -XX:+UseLargePages"                           # 使用大页内存
JVM_OPTS="$JVM_OPTS -XX:LargePageSizeInBytes=2m"                  # 设置大页大小为 2MB
JVM_OPTS="$JVM_OPTS -XX:+DisableExplicitGC"                       # 禁用显式 GC
JVM_OPTS="$JVM_OPTS -XX:+ParallelRefProcEnabled"                  # 并行引用处理
JVM_OPTS="$JVM_OPTS -XX:ConcGCThreads=32"                         # 并发 GC 线程数
JVM_OPTS="$JVM_OPTS -XX:ParallelGCThreads=32"                     # 并行 GC 线程数

# Netty 相关优化
JVM_OPTS="$JVM_OPTS -Dio.netty.leakDetection.level=disabled"      # 禁用内存泄漏检测
JVM_OPTS="$JVM_OPTS -Dio.netty.allocator.numHeapArenas=32"        # 堆内存区域数量
JVM_OPTS="$JVM_OPTS -Dio.netty.allocator.numDirectArenas=32"      # 直接内存区域数量
JVM_OPTS="$JVM_OPTS -Dio.netty.allocator.maxOrder=11"             # 最大分配大小
JVM_OPTS="$JVM_OPTS -Dio.netty.allocator.pageSize=8192"           # 页大小
JVM_OPTS="$JVM_OPTS -Dio.netty.allocator.maxCachedBufferCapacity=131072" # 最大缓存容量
JVM_OPTS="$JVM_OPTS -Dio.netty.allocator.tinyCacheSize=1024"      # 小缓存大小
JVM_OPTS="$JVM_OPTS -Dio.netty.allocator.smallCacheSize=512"      # 小缓存大小
JVM_OPTS="$JVM_OPTS -Dio.netty.allocator.normalCacheSize=256"     # 普通缓存大小
JVM_OPTS="$JVM_OPTS -Dio.netty.noPreferDirect=false"              # 优先使用直接内存
JVM_OPTS="$JVM_OPTS -Dio.netty.recycler.maxCapacityPerThread=8192" # 每个线程的最大容量
JVM_OPTS="$JVM_OPTS -Dio.netty.buffer.checkBounds=false"          # 禁用边界检查
JVM_OPTS="$JVM_OPTS -Dio.netty.buffer.checkAccessible=false"      # 禁用可访问性检查

# Vert.x 相关优化
JVM_OPTS="$JVM_OPTS -Dvertx.maxEventLoopExecuteTime=20000000000"  # 最大事件循环执行时间（20秒）
JVM_OPTS="$JVM_OPTS -Dvertx.maxWorkerExecuteTime=240000000000"    # 最大工作线程执行时间（240秒）
JVM_OPTS="$JVM_OPTS -Dvertx.disableMetrics=false"                 # 启用 Vert.x 指标
JVM_OPTS="$JVM_OPTS -Dvertx.preferNativeTransport=true"           # 使用本地传输
JVM_OPTS="$JVM_OPTS -Dvertx.disableTCCL=true"                     # 禁用线程上下文类加载器
JVM_OPTS="$JVM_OPTS -Dvertx.threadChecks=false"                   # 禁用线程检查
JVM_OPTS="$JVM_OPTS -Dvertx.disableContextTimings=true"           # 禁用上下文计时
JVM_OPTS="$JVM_OPTS -Dvertx.disableHttpHeadersValidation=true"    # 禁用 HTTP 头验证
JVM_OPTS="$JVM_OPTS -Dvertx.eventLoopPoolSize=64"                 # 事件循环线程池大小
JVM_OPTS="$JVM_OPTS -Dvertx.workerPoolSize=256"                   # 工作线程池大小
JVM_OPTS="$JVM_OPTS -Dvertx.internalBlockingPoolSize=128"         # 内部阻塞线程池大小
JVM_OPTS="$JVM_OPTS -Dvertx.fileResolverCachingEnabled=true"      # 启用文件解析器缓存
JVM_OPTS="$JVM_OPTS -Dvertx.disableWebsockets=false"              # 启用 WebSockets
JVM_OPTS="$JVM_OPTS -Dvertx.flashPolicyHandler=false"             # 禁用 Flash 策略处理器
JVM_OPTS="$JVM_OPTS -Dvertx.warningExceptionTime=10000000000"     # 警告异常时间（10秒）
JVM_OPTS="$JVM_OPTS -Dvertx.blockedThreadCheckInterval=5000"      # 阻塞线程检查间隔（5秒）
JVM_OPTS="$JVM_OPTS -Dapix.vertx.config.path=src/main/resources/vertx-high-performance.json" # Vert.x 配置文件路径

# 调试和监控设置
JVM_OPTS="$JVM_OPTS -XX:+HeapDumpOnOutOfMemoryError"              # 内存溢出时生成堆转储
JVM_OPTS="$JVM_OPTS -XX:HeapDumpPath=./heapdump.hprof"            # 堆转储路径
JVM_OPTS="$JVM_OPTS -XX:+PrintGCDetails"                          # 打印 GC 详细信息
JVM_OPTS="$JVM_OPTS -XX:+PrintGCDateStamps"                       # 打印 GC 时间戳
JVM_OPTS="$JVM_OPTS -Xlog:gc*=info:file=gc.log:time,uptime,level,tags:filecount=5,filesize=100m" # GC 日志

# 显示 JVM 参数
echo "JVM 参数: $JVM_OPTS"

# 创建 Vert.x 高性能配置文件
echo "创建 Vert.x 高性能配置文件..."
mkdir -p src/main/resources
cat > src/main/resources/vertx-high-performance.json << EOF
{
  "eventLoopPoolSize": 64,
  "workerPoolSize": 256,
  "internalBlockingPoolSize": 128,
  "blockedThreadCheckInterval": 5000,
  "maxEventLoopExecuteTime": 20000000000,
  "maxWorkerExecuteTime": 240000000000,
  "warningExceptionTime": 10000000000,
  "fileResolverCachingEnabled": true,
  "preferNativeTransport": true,
  "eventBus": {
    "acceptBacklog": 100000,
    "clientAuth": "NONE",
    "connectTimeout": 120000,
    "idleTimeout": 0,
    "receiveBufferSize": 131072,
    "reconnectAttempts": 0,
    "reconnectInterval": 1000,
    "reuseAddress": true,
    "reusePort": true,
    "sendBufferSize": 131072,
    "soLinger": -1,
    "ssl": false,
    "tcpKeepAlive": true,
    "tcpNoDelay": true,
    "trafficClass": -1,
    "trustAll": true
  },
  "httpServerOptions": {
    "acceptBacklog": 100000,
    "compressionLevel": 6,
    "compressionSupported": true,
    "decompressionSupported": true,
    "handle100ContinueAutomatically": true,
    "idleTimeout": 300,
    "maxChunkSize": 65536,
    "maxHeaderSize": 32768,
    "maxInitialLineLength": 16384,
    "maxWebSocketFrameSize": 262144,
    "maxWebSocketMessageSize": 1048576,
    "reuseAddress": true,
    "reusePort": true,
    "tcpFastOpen": true,
    "tcpKeepAlive": true,
    "tcpNoDelay": true,
    "tcpQuickAck": true
  },
  "http2Settings": {
    "headerTableSize": 8192,
    "initialWindowSize": 1048576,
    "maxConcurrentStreams": 100000,
    "maxFrameSize": 16384,
    "maxHeaderListSize": 32768,
    "pushEnabled": false
  }
}
EOF

# 运行应用程序
echo "启动应用程序..."
java $JVM_OPTS -jar build/libs/apix-1.0.0-fat.jar
