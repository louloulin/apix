#!/bin/bash

# APIX Gateway Native Image 构建和性能测试脚本

echo "===== 开始构建 APIX Gateway Native Image ====="

# 检查 GraalVM 是否已安装
if ! command -v native-image &> /dev/null; then
    echo "错误: GraalVM native-image 未找到。请确保已安装 GraalVM 并设置了 JAVA_HOME。"
    echo "安装指南: https://www.graalvm.org/latest/docs/getting-started/macos/"
    exit 1
fi

# 设置系统参数
echo "设置系统参数..."
ulimit -n 1000000 || echo "警告: 无法设置文件描述符限制，可能需要 root 权限"

# 设置操作系统参数（需要 root 权限）
if [ "$(uname)" == "Linux" ]; then
  echo "尝试设置 Linux 内核参数..."
  sudo sysctl -w net.core.somaxconn=100000 || echo "警告: 无法设置 net.core.somaxconn"
  sudo sysctl -w net.ipv4.tcp_max_syn_backlog=100000 || echo "警告: 无法设置 net.ipv4.tcp_max_syn_backlog"
  sudo sysctl -w net.ipv4.ip_local_port_range="1024 65535" || echo "警告: 无法设置 net.ipv4.ip_local_port_range"
  sudo sysctl -w net.ipv4.tcp_tw_reuse=1 || echo "警告: 无法设置 net.ipv4.tcp_tw_reuse"
  sudo sysctl -w net.ipv4.tcp_fin_timeout=5 || echo "警告: 无法设置 net.ipv4.tcp_fin_timeout"
  sudo sysctl -w net.core.netdev_max_backlog=250000 || echo "警告: 无法设置 net.core.netdev_max_backlog"
elif [ "$(uname)" == "Darwin" ]; then
  echo "在 macOS 上设置系统参数..."
  sudo sysctl -w kern.maxfiles=1000000 || echo "警告: 无法设置 kern.maxfiles"
  sudo sysctl -w kern.maxfilesperproc=1000000 || echo "警告: 无法设置 kern.maxfilesperproc"
fi

# 检测可用处理器数量
AVAILABLE_PROCESSORS=$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 8)
echo "检测到 $AVAILABLE_PROCESSORS 个处理器"

# 创建 Vert.x 10万并发配置文件
echo "创建 Vert.x 10万并发配置文件..."
mkdir -p src/main/resources
cat > src/main/resources/vertx-100k.json << EOF
{
  "eventLoopPoolSize": $((AVAILABLE_PROCESSORS*2)),
  "workerPoolSize": $((AVAILABLE_PROCESSORS*8)),
  "internalBlockingPoolSize": $((AVAILABLE_PROCESSORS*4)),
  "blockedThreadCheckInterval": 10000,
  "maxEventLoopExecuteTime": 60000000000,
  "maxWorkerExecuteTime": 600000000000,
  "warningExceptionTime": 30000000000,
  "fileResolverCachingEnabled": true,
  "preferNativeTransport": true,
  "eventBus": {
    "acceptBacklog": 100000,
    "clientAuth": "NONE",
    "connectTimeout": 30000,
    "idleTimeout": 0,
    "receiveBufferSize": 262144,
    "reconnectAttempts": 0,
    "reconnectInterval": 1000,
    "reuseAddress": true,
    "reusePort": true,
    "sendBufferSize": 262144,
    "soLinger": -1,
    "ssl": false,
    "tcpKeepAlive": true,
    "tcpNoDelay": true,
    "trafficClass": -1,
    "trustAll": true
  },
  "httpServerOptions": {
    "acceptBacklog": 100000,
    "compressionLevel": 1,
    "compressionSupported": false,
    "decompressionSupported": false,
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

# 构建 Native Image
echo "开始构建 Native Image..."
./gradlew nativeCompile

# 检查构建是否成功
if [ ! -f "build/native/nativeCompile/apix" ]; then
    echo "错误: Native Image 构建失败。请检查日志获取详细信息。"
    exit 1
fi

echo "Native Image 构建成功: build/native/nativeCompile/apix"

# 运行 Native Image
echo "启动 Native Image..."
BUILD_DIR="build/native/nativeCompile"
cd $BUILD_DIR

# 设置配置文件路径
export APIX_CONFIG_PATH="../../resources/main/vertx-100k.json"

# 启动应用
./apix &
PID=$!
echo "APIX Gateway Native Image 已启动，进程ID: $PID"

# 等待应用启动
echo "等待应用启动..."
sleep 5

# 检查应用是否正常启动
curl -s http://localhost:8080/api/hello
if [ $? -ne 0 ]; then
    echo "警告: 无法访问应用程序，请检查日志"
    exit 1
fi

echo "应用已成功启动，开始性能测试..."

# 返回到项目根目录
cd ../../..

# 运行 k6 性能测试
echo "运行 k6 性能测试..."
k6 run k6-1k-test.js

# 测试完成后关闭应用
echo "测试完成，关闭应用..."
kill $PID

echo "===== APIX Gateway Native Image 测试完成 ====="
