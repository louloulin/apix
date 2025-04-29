#!/bin/bash

# 运行 10 万并发连接测试

echo "===== 开始 10 万并发连接测试 ====="

# 1. 停止所有正在运行的 Java 进程
echo "停止所有正在运行的 Java 进程..."
pkill -f "java.*apix" || true
./gradlew --stop || true
sleep 5

# 2. 启动应用程序
echo "启动应用程序..."
./run-100k.sh &
APP_PID=$!

# 等待应用程序启动
echo "等待应用程序启动..."
sleep 15

# 3. 验证应用程序是否正常运行
echo "验证应用程序是否正常运行..."
RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/hello)
if [ "$RESPONSE" != "200" ]; then
    echo "应用程序未正常启动，HTTP 状态码: $RESPONSE"
    echo "尝试访问其他端点..."
    
    echo "访问 /hello 端点:"
    curl -v http://localhost:8080/hello
    
    echo "访问根路径:"
    curl -v http://localhost:8080/
    
    echo "终止测试..."
    kill $APP_PID || true
    exit 1
fi

echo "应用程序正常运行，HTTP 状态码: $RESPONSE"

# 4. 运行 10 万并发连接测试
echo "运行 10 万并发连接测试..."
k6 run k6-100k-test.js

# 5. 停止应用程序
echo "停止应用程序..."
kill $APP_PID || true

echo "===== 10 万并发连接测试完成 ====="
