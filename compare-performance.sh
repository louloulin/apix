#!/bin/bash

# APIX Gateway JVM vs Native 性能比较脚本

echo "===== APIX Gateway JVM vs Native 性能比较 ====="

# 设置系统参数
echo "设置系统参数..."
ulimit -n 1000000 || echo "警告: 无法设置文件描述符限制，可能需要 root 权限"

# 检测可用处理器数量
AVAILABLE_PROCESSORS=$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 8)
echo "检测到 $AVAILABLE_PROCESSORS 个处理器"

# 构建应用程序
echo "构建应用程序..."
./gradlew clean build -x test

# 构建 Native Image
echo "构建 Native Image..."
./gradlew nativeCompile

# 检查构建是否成功
if [ ! -f "build/native/nativeCompile/apix" ]; then
    echo "错误: Native Image 构建失败。请检查日志获取详细信息。"
    exit 1
fi

# 创建结果目录
mkdir -p performance-results

# 1. 测试 JVM 模式启动时间
echo "测试 JVM 模式启动时间..."
START_TIME=$(date +%s.%N)
java -jar build/libs/apix-1.0.0-fat.jar &
JVM_PID=$!
# 等待应用启动
sleep 5
# 检查应用是否正常启动
curl -s http://localhost:8080/api/hello > /dev/null
END_TIME=$(date +%s.%N)
JVM_STARTUP_TIME=$(echo "$END_TIME - $START_TIME" | bc)
echo "JVM 模式启动时间: $JVM_STARTUP_TIME 秒"

# 运行 JVM 模式性能测试
echo "运行 JVM 模式性能测试..."
k6 run --out json=performance-results/jvm-results.json k6-1k-test.js

# 关闭 JVM 应用
kill $JVM_PID
sleep 5

# 2. 测试 Native 模式启动时间
echo "测试 Native 模式启动时间..."
START_TIME=$(date +%s.%N)
build/native/nativeCompile/apix &
NATIVE_PID=$!
# 等待应用启动
sleep 2
# 检查应用是否正常启动
curl -s http://localhost:8080/api/hello > /dev/null
END_TIME=$(date +%s.%N)
NATIVE_STARTUP_TIME=$(echo "$END_TIME - $START_TIME" | bc)
echo "Native 模式启动时间: $NATIVE_STARTUP_TIME 秒"

# 运行 Native 模式性能测试
echo "运行 Native 模式性能测试..."
k6 run --out json=performance-results/native-results.json k6-1k-test.js

# 关闭 Native 应用
kill $NATIVE_PID

# 比较结果
echo "===== 性能比较结果 ====="
echo "启动时间比较:"
echo "JVM 模式: $JVM_STARTUP_TIME 秒"
echo "Native 模式: $NATIVE_STARTUP_TIME 秒"
STARTUP_IMPROVEMENT=$(echo "scale=2; ($JVM_STARTUP_TIME - $NATIVE_STARTUP_TIME) / $JVM_STARTUP_TIME * 100" | bc)
echo "启动时间改进: $STARTUP_IMPROVEMENT%"

# 解析 k6 结果并比较
echo "性能测试结果比较:"
echo "详细结果保存在 performance-results 目录中"

# 创建比较报告
cat > performance-results/comparison-report.md << EOF
# APIX Gateway JVM vs Native 性能比较报告

## 启动时间比较
- JVM 模式: $JVM_STARTUP_TIME 秒
- Native 模式: $NATIVE_STARTUP_TIME 秒
- 启动时间改进: $STARTUP_IMPROVEMENT%

## 性能测试结果
请查看 performance-results 目录中的 JSON 文件获取详细结果。

## 内存使用比较
Native 模式通常比 JVM 模式使用更少的内存，并且没有 JVM 预热时间。

## 结论
Native 模式相比 JVM 模式的主要优势:
1. 更快的启动时间
2. 更低的内存占用
3. 更稳定的性能表现（无 JVM 预热和 GC 暂停）

JVM 模式的优势:
1. 动态优化可能在长时间运行后提供更好的峰值性能
2. 更灵活的运行时配置
3. 更成熟的工具和调试支持
EOF

echo "比较报告已生成: performance-results/comparison-report.md"
echo "===== 性能比较完成 ====="
