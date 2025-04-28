#!/bin/bash

# 设置操作系统参数（需要 root 权限）
# sudo sysctl -w net.core.somaxconn=65535
# sudo sysctl -w net.ipv4.tcp_max_syn_backlog=65535
# sudo sysctl -w net.ipv4.ip_local_port_range="1024 65535"
# sudo sysctl -w net.ipv4.tcp_tw_reuse=1
# sudo sysctl -w net.ipv4.tcp_fin_timeout=30
# sudo sysctl -w net.core.netdev_max_backlog=65535
# sudo ulimit -n 1000000

# 构建项目
./gradlew build -x test

# 运行 APIX 网关，使用优化的 JVM 参数以支持 10 万并发
java \
  -server \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=50 \
  -XX:+AlwaysPreTouch \
  -XX:+DisableExplicitGC \
  -XX:+UseStringDeduplication \
  -XX:+UseNUMA \
  -XX:+UseCompressedOops \
  -XX:+OptimizeStringConcat \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=./heapdump.hprof \
  -XX:+UnlockExperimentalVMOptions \
  -XX:G1NewSizePercent=30 \
  -XX:G1MaxNewSizePercent=50 \
  -XX:G1HeapRegionSize=16M \
  -XX:G1ReservePercent=15 \
  -XX:InitiatingHeapOccupancyPercent=20 \
  -Xms4g \
  -Xmx4g \
  -Dio.netty.leakDetection.level=disabled \
  -Dio.netty.recycler.maxCapacity=4096 \
  -Dio.netty.allocator.numDirectArenas=$(nproc) \
  -Dio.netty.allocator.numHeapArenas=$(nproc) \
  -Dio.netty.allocator.tinyCacheSize=4096 \
  -Dio.netty.allocator.smallCacheSize=4096 \
  -Dio.netty.allocator.normalCacheSize=4096 \
  -Dio.netty.allocator.maxOrder=11 \
  -Djava.net.preferIPv4Stack=true \
  -Dvertx.disableMetrics=true \
  -Dvertx.disableH2c=false \
  -Dvertx.disableWebsockets=false \
  -Dvertx.flashPolicyHandler=false \
  -Dvertx.threadChecks=false \
  -Dvertx.disableContextTimings=true \
  -Dvertx.disableTCCL=true \
  -Dvertx.disableHttpHeadersValidation=true \
  -Dapix.config.path=./config/apix.json \
  -jar ./build/libs/apix-1.0-SNAPSHOT.jar
