#!/bin/bash

# 执行 20 万并发连接的 1 分钟压测

echo "===== 开始 20 万并发连接的 1 分钟压测 ====="

# 1. 停止所有正在运行的 Java 进程
echo "停止所有正在运行的 Java 进程..."
pkill -f "java.*apix" || true
./gradlew --stop || true
sleep 5

# 2. 优化系统参数
echo "优化系统参数..."
./optimize-for-200k.sh

# 3. 构建应用程序
echo "构建应用程序..."
./gradlew build -x test

# 4. 启动应用程序
echo "启动应用程序..."
./run-for-200k.sh &
APP_PID=$!

# 等待应用程序启动
echo "等待应用程序启动..."
sleep 10

# 5. 验证应用程序是否正常运行
echo "验证应用程序是否正常运行..."
curl -v http://localhost:8080/

# 6. 运行压力测试
echo "运行 20 万并发连接的 1 分钟压测..."
k6 run k6-200k-1min.js

# 7. 停止应用程序
echo "停止应用程序..."
kill $APP_PID || true

echo "===== 压测完成 ====="
