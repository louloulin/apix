#!/bin/bash

# 完整的性能测试执行脚本

echo "===== 开始性能测试 ====="

# 1. 停止所有正在运行的 Java 进程
echo "停止所有正在运行的 Java 进程..."
pkill -f "java.*apix" || true
./gradlew --stop || true
sleep 5

# 2. 优化系统参数
echo "优化系统参数..."
./optimize-system-priority.sh

# 3. 构建应用程序
echo "构建应用程序..."
./gradlew build -x test

# 4. 启动系统监控
echo "启动系统监控..."
./monitor-system.sh &
MONITOR_PID=$!

# 5. 启动应用程序
echo "启动应用程序..."
./run-optimized-priority.sh &
APP_PID=$!

# 等待应用程序启动
echo "等待应用程序启动..."
sleep 10

# 6. 验证应用程序是否正常运行
echo "验证应用程序是否正常运行..."
RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/hello)
if [ "$RESPONSE" != "200" ]; then
    echo "应用程序未正常启动，HTTP 状态码: $RESPONSE"
    kill $APP_PID
    kill $MONITOR_PID
    exit 1
fi
echo "应用程序正常运行，HTTP 状态码: $RESPONSE"

# 7. 运行性能测试
echo "运行性能测试..."
k6 run k6-priority-load.js

# 8. 停止应用程序和监控
echo "停止应用程序和监控..."
kill $APP_PID
kill $MONITOR_PID

echo "===== 性能测试完成 ====="
