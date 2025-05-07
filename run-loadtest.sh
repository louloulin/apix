#!/bin/bash

# 确保脚本在出错时退出
set -e

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== APIX Gateway 压力测试 ===${NC}"

# 检查k6是否已安装
if ! command -v k6 &> /dev/null; then
    echo -e "${RED}错误: k6 未安装${NC}"
    echo "请按照以下说明安装 k6: https://k6.io/docs/getting-started/installation/"
    exit 1
fi

# 检查应用是否正在运行
echo -e "${YELLOW}检查应用是否正在运行...${NC}"
if ! curl -s http://localhost:8080/ping > /dev/null; then
    echo -e "${RED}错误: 应用未运行或无法访问${NC}"
    echo "请确保应用已启动并监听在 localhost:8080"
    exit 1
fi

echo -e "${GREEN}应用正在运行，开始压力测试...${NC}"

# 运行标准负载测试
echo -e "${YELLOW}运行标准负载测试...${NC}"
k6 run loadtest.js

# 等待一段时间让系统恢复
echo -e "${YELLOW}等待系统恢复 (10秒)...${NC}"
sleep 10

# 运行高性能负载测试
echo -e "${YELLOW}运行高性能负载测试...${NC}"
k6 run loadtest-high-perf.js

echo -e "${GREEN}压力测试完成!${NC}"
