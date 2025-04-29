#!/bin/bash

# JVM 启动脚本 - 专注于支持20万并发连接

# 构建应用程序
echo "构建应用程序..."
./gradlew build -x test

# 设置 JVM 参数 - 专注于支持高并发
JVM_OPTS="-server"
JVM_OPTS="$JVM_OPTS -Xms4g -Xmx20g"                                # 增加堆内存大小
JVM_OPTS="$JVM_OPTS -XX:+UseG1GC"                                 # 使用 G1 垃圾收集器
JVM_OPTS="$JVM_OPTS -XX:MaxGCPauseMillis=100"                     # 最大 GC 暂停时间
JVM_OPTS="$JVM_OPTS -XX:+AlwaysPreTouch"                          # 预分配内存
JVM_OPTS="$JVM_OPTS -XX:+DisableExplicitGC"                       # 禁用显式 GC
JVM_OPTS="$JVM_OPTS -XX:MetaspaceSize=256m"                       # 增加元空间初始大小
JVM_OPTS="$JVM_OPTS -XX:MaxMetaspaceSize=512m"                    # 增加元空间最大大小

# Netty 优化参数 - 专注于支持高并发
JVM_OPTS="$JVM_OPTS -Dio.netty.leakDetection.level=disabled"      # 禁用 Netty 内存泄漏检测
JVM_OPTS="$JVM_OPTS -Dio.netty.allocator.numHeapArenas=32"        # Netty 堆内存区域数量
JVM_OPTS="$JVM_OPTS -Dio.netty.allocator.numDirectArenas=32"      # Netty 直接内存区域数量
JVM_OPTS="$JVM_OPTS -Dio.netty.allocator.maxOrder=11"             # Netty 最大分配大小
JVM_OPTS="$JVM_OPTS -Dio.netty.allocator.pageSize=8192"           # Netty 页大小
JVM_OPTS="$JVM_OPTS -Dio.netty.allocator.maxCachedBufferCapacity=32768" # Netty 最大缓存容量

# Vert.x 优化参数 - 专注于支持高并发
JVM_OPTS="$JVM_OPTS -Dvertx.maxEventLoopExecuteTime=10000000000"  # 最大事件循环执行时间（10秒）
JVM_OPTS="$JVM_OPTS -Dvertx.maxWorkerExecuteTime=120000000000"    # 最大工作线程执行时间（120秒）
JVM_OPTS="$JVM_OPTS -Dvertx.disableMetrics=false"                 # 启用 Vert.x 指标
JVM_OPTS="$JVM_OPTS -Dvertx.preferNativeTransport=true"           # 使用本地传输
JVM_OPTS="$JVM_OPTS -Dvertx.disableTCCL=true"                     # 禁用线程上下文类加载器
JVM_OPTS="$JVM_OPTS -Dvertx.threadChecks=false"                   # 禁用线程检查
JVM_OPTS="$JVM_OPTS -Dvertx.disableContextTimings=true"           # 禁用上下文计时

# 运行应用程序
echo "启动应用程序..."
echo "JVM 参数: $JVM_OPTS"
java $JVM_OPTS -jar build/libs/apix-1.0.0-fat.jar
