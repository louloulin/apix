#!/bin/bash

# 测试 APIX Gateway 集群启动
# 此脚本用于验证 APIX Gateway 集群中的节点是否能够正常启动，没有端口冲突

echo "开始测试 APIX Gateway 集群启动..."

# 停止所有现有的 APIX Gateway 进程
echo "停止所有现有的 APIX Gateway 进程..."
pkill -9 -f "java -Dapix.config.path"
sleep 2

# 启动控制平面节点
echo "启动控制平面节点..."
java -Dapix.config.path=$(pwd)/config-control-plane.json -jar build/libs/apix-1.0.0-fat.jar > control-plane.log 2>&1 &
CONTROL_PLANE_PID=$!
echo "控制平面节点 PID: $CONTROL_PLANE_PID"

# 等待控制平面节点启动
echo "等待控制平面节点启动..."
sleep 10

# 检查控制平面节点是否正常启动
echo "检查控制平面节点是否正常启动..."
if curl -s http://localhost:8070/ping > /dev/null; then
    echo "控制平面节点启动成功！"
else
    echo "控制平面节点启动失败！"
    exit 1
fi

# 启动数据平面节点 1
echo "启动数据平面节点 1..."
java -Dapix.config.path=$(pwd)/config-node1.json -jar build/libs/apix-1.0.0-fat.jar > node1.log 2>&1 &
NODE1_PID=$!
echo "数据平面节点 1 PID: $NODE1_PID"

# 等待数据平面节点 1 启动
echo "等待数据平面节点 1 启动..."
sleep 10

# 检查数据平面节点 1 是否正常启动
echo "检查数据平面节点 1 是否正常启动..."
if curl -s http://localhost:9280/ping > /dev/null; then
    echo "数据平面节点 1 启动成功！"
else
    echo "数据平面节点 1 启动失败！"
    exit 1
fi

# 启动数据平面节点 2
echo "启动数据平面节点 2..."
java -Dapix.config.path=$(pwd)/config-node2.json -jar build/libs/apix-1.0.0-fat.jar > node2.log 2>&1 &
NODE2_PID=$!
echo "数据平面节点 2 PID: $NODE2_PID"

# 等待数据平面节点 2 启动
echo "等待数据平面节点 2 启动..."
sleep 10

# 检查数据平面节点 2 是否正常启动
echo "检查数据平面节点 2 是否正常启动..."
if curl -s http://localhost:9090/ping > /dev/null; then
    echo "数据平面节点 2 启动成功！"
else
    echo "数据平面节点 2 启动失败！"
    exit 1
fi

# 启动数据平面节点 3
echo "启动数据平面节点 3..."
java -Dapix.config.path=$(pwd)/config-node3.json -jar build/libs/apix-1.0.0-fat.jar > node3.log 2>&1 &
NODE3_PID=$!
echo "数据平面节点 3 PID: $NODE3_PID"

# 等待数据平面节点 3 启动
echo "等待数据平面节点 3 启动..."
sleep 10

# 检查数据平面节点 3 是否正常启动
echo "检查数据平面节点 3 是否正常启动..."
if curl -s http://localhost:10090/ping > /dev/null; then
    echo "数据平面节点 3 启动成功！"
else
    echo "数据平面节点 3 启动失败！"
    exit 1
fi

echo "所有节点启动成功！"

# 检查集群状态
echo "检查集群状态..."
curl -s http://localhost:8070/api/cluster/status

# 停止所有节点
echo "测试完成，停止所有节点..."
kill $CONTROL_PLANE_PID $NODE1_PID $NODE2_PID $NODE3_PID
sleep 2

echo "测试完成！"
