#!/bin/bash

# 查找并杀死占用指定端口的进程
# 用法: ./kill-port.sh <端口号>

if [ $# -ne 1 ]; then
    echo "用法: $0 <端口号>"
    exit 1
fi

PORT=$1
echo "查找占用端口 $PORT 的进程..."

# 检测操作系统类型
if [ "$(uname)" == "Darwin" ]; then
    # macOS
    PID=$(lsof -i :$PORT -t)
    if [ -z "$PID" ]; then
        echo "没有进程占用端口 $PORT"
        exit 0
    fi
    
    echo "发现以下进程占用端口 $PORT:"
    lsof -i :$PORT
    
    echo "正在终止进程..."
    for pid in $PID; do
        echo "终止进程 $pid"
        kill -9 $pid
    done
    
    echo "端口 $PORT 已释放"
else
    # Linux
    PID=$(netstat -tulpn 2>/dev/null | grep ":$PORT " | awk '{print $7}' | cut -d'/' -f1)
    if [ -z "$PID" ]; then
        echo "没有进程占用端口 $PORT"
        exit 0
    fi
    
    echo "发现以下进程占用端口 $PORT:"
    netstat -tulpn | grep ":$PORT "
    
    echo "正在终止进程..."
    for pid in $PID; do
        echo "终止进程 $pid"
        kill -9 $pid
    done
    
    echo "端口 $PORT 已释放"
fi
