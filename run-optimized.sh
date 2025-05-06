#!/bin/bash

# 优化的JVM参数，用于高性能场景
JVM_OPTS=""

# 内存设置
JVM_OPTS="$JVM_OPTS -Xms4g -Xmx4g"  # 固定堆大小为4GB
JVM_OPTS="$JVM_OPTS -XX:+AlwaysPreTouch"  # 预分配所有内存
JVM_OPTS="$JVM_OPTS -XX:MaxDirectMemorySize=2g"  # 直接内存上限为2GB

# 垃圾收集器设置 - 使用ZGC
JVM_OPTS="$JVM_OPTS -XX:+UseZGC"  # 使用ZGC垃圾收集器
JVM_OPTS="$JVM_OPTS -XX:ZCollectionInterval=5"  # ZGC收集间隔
JVM_OPTS="$JVM_OPTS -XX:ConcGCThreads=2"  # 并发GC线程数
JVM_OPTS="$JVM_OPTS -XX:ZUncommitDelay=30"  # 内存释放延迟

# JIT编译器优化
JVM_OPTS="$JVM_OPTS -XX:+TieredCompilation"  # 启用分层编译
JVM_OPTS="$JVM_OPTS -XX:+UseStringDeduplication"  # 启用字符串去重
JVM_OPTS="$JVM_OPTS -XX:CompileThreshold=1000"  # 降低编译阈值
# 线程设置
JVM_OPTS="$JVM_OPTS -XX:+UseThreadPriorities"  # 使用线程优先级
JVM_OPTS="$JVM_OPTS -XX:ThreadPriorityPolicy=1"  # 线程优先级策略
JVM_OPTS="$JVM_OPTS -Xss256k"  # 减小线程栈大小

# 网络设置
JVM_OPTS="$JVM_OPTS -Djava.net.preferIPv4Stack=true"  # 优先使用IPv4
JVM_OPTS="$JVM_OPTS -Dsun.net.inetaddr.ttl=30"  # DNS缓存TTL

# 系统属性
JVM_OPTS="$JVM_OPTS -Dio.netty.allocator.numDirectArenas=32"  # Netty直接内存分配区域数
JVM_OPTS="$JVM_OPTS -Dio.netty.noPreferDirect=false"  # 优先使用直接内存
JVM_OPTS="$JVM_OPTS -Dio.netty.recycler.maxCapacity=4096"  # Netty对象回收器容量
JVM_OPTS="$JVM_OPTS -Dio.netty.leakDetection.level=disabled"  # 禁用内存泄漏检测

# 日志设置
JVM_OPTS="$JVM_OPTS -Xlog:gc*=info:file=gc.log:time,uptime,level,tags:filecount=5,filesize=100m"

# 系统参数优化
ulimit -n 1000000  # 增加文件描述符限制

# 启动应用
echo "Starting APIX with optimized JVM parameters..."
java $JVM_OPTS -jar build/libs/apix-1.0.0-fat.jar
