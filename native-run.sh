#!/bin/bash

# 脚本用于编译和运行Native Image

echo "===== 开始编译和运行Native Image ====="

# 1. 清理和构建项目
echo "清理和构建项目..."
./gradlew clean build -x test

# 2. 编译Native Image
echo "编译Native Image..."
./gradlew nativeCompile

# 检查编译是否成功
if [ ! -f "build/native/nativeCompile/apix" ]; then
    echo "错误: Native Image 编译失败。请检查日志获取详细信息。"
    exit 1
fi

# 3. 运行Native Image
echo "运行Native Image..."
cd build/native/nativeCompile
./apix &
PID=$!

# 等待应用启动
echo "等待应用启动..."
sleep 5

# 检查应用是否正常启动
curl -s http://localhost:8080/api/hello
if [ $? -ne 0 ]; then
    echo "警告: 无法访问应用程序，请检查日志"
    kill $PID
    exit 1
fi

echo "应用已成功启动，进程ID: $PID"
echo "按Ctrl+C停止应用..."

# 等待用户按Ctrl+C
wait $PID

echo "===== Native Image 运行结束 ====="
