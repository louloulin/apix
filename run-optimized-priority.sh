#!/bin/bash

# 优先级高的 JVM 优化启动脚本

# 构建应用程序
echo "构建应用程序..."
./gradlew build -x test

# 设置优先级高的 JVM 参数
JVM_OPTS="-server"
JVM_OPTS="$JVM_OPTS -Xms4g -Xmx4g"                                # 增加堆内存大小
JVM_OPTS="$JVM_OPTS -XX:+UseG1GC"                                 # 使用 G1 垃圾收集器
JVM_OPTS="$JVM_OPTS -XX:MaxGCPauseMillis=50"                      # 最大 GC 暂停时间
JVM_OPTS="$JVM_OPTS -XX:+AlwaysPreTouch"                          # 预分配内存
JVM_OPTS="$JVM_OPTS -XX:+DisableExplicitGC"                       # 禁用显式 GC
JVM_OPTS="$JVM_OPTS -XX:MetaspaceSize=256m"                       # 增加元空间初始大小
JVM_OPTS="$JVM_OPTS -XX:MaxMetaspaceSize=512m"                    # 增加元空间最大大小
JVM_OPTS="$JVM_OPTS -XX:+HeapDumpOnOutOfMemoryError"              # 内存溢出时生成堆转储
JVM_OPTS="$JVM_OPTS -XX:HeapDumpPath=./heapdump.hprof"            # 堆转储路径

# Netty 优化参数 - 高优先级
JVM_OPTS="$JVM_OPTS -Dio.netty.leakDetection.level=disabled"      # 禁用 Netty 内存泄漏检测
JVM_OPTS="$JVM_OPTS -Dio.netty.allocator.numHeapArenas=32"        # Netty 堆内存区域数量
JVM_OPTS="$JVM_OPTS -Dio.netty.allocator.numDirectArenas=32"      # Netty 直接内存区域数量
JVM_OPTS="$JVM_OPTS -Dio.netty.allocator.maxOrder=11"             # Netty 最大分配大小

# Vert.x 优化参数 - 高优先级
JVM_OPTS="$JVM_OPTS -Dvertx.maxEventLoopExecuteTime=5000000000"   # 最大事件循环执行时间（5秒）
JVM_OPTS="$JVM_OPTS -Dvertx.maxWorkerExecuteTime=120000000000"    # 最大工作线程执行时间（120秒）
JVM_OPTS="$JVM_OPTS -Dvertx.disableMetrics=false"                 # 启用 Vert.x 指标
JVM_OPTS="$JVM_OPTS -Dvertx.preferNativeTransport=true"           # 使用本地传输

# 运行应用程序
echo "启动应用程序..."
echo "JVM 参数: $JVM_OPTS"
java $JVM_OPTS -jar build/libs/apix-all.jar
