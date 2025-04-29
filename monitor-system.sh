#!/bin/bash

# 系统资源监控脚本

# 监控间隔（秒）
INTERVAL=5

# 监控持续时间（秒）
DURATION=600

# 输出文件
OUTPUT_FILE="system_metrics_$(date +%Y%m%d_%H%M%S).log"

echo "开始监控系统资源，输出到 $OUTPUT_FILE"
echo "监控间隔: ${INTERVAL}秒，持续时间: ${DURATION}秒"
echo "按 Ctrl+C 停止监控"
echo ""

# 写入标题
echo "时间戳,CPU使用率(%),内存使用率(%),可用内存(MB),TCP连接数,文件描述符数" > $OUTPUT_FILE

# 计算迭代次数
ITERATIONS=$((DURATION / INTERVAL))

for ((i=1; i<=$ITERATIONS; i++)); do
    # 获取时间戳
    TIMESTAMP=$(date +"%Y-%m-%d %H:%M:%S")
    
    # 获取 CPU 使用率
    if [[ "$(uname)" == "Darwin" ]]; then
        # macOS
        CPU_USAGE=$(top -l 1 | grep "CPU usage" | awk '{print $3}' | tr -d '%')
    else
        # Linux
        CPU_USAGE=$(top -bn1 | grep "Cpu(s)" | awk '{print $2 + $4}')
    fi
    
    # 获取内存使用率和可用内存
    if [[ "$(uname)" == "Darwin" ]]; then
        # macOS
        MEM_USAGE=$(top -l 1 | grep "PhysMem" | awk '{print $2}' | tr -d 'M')
        MEM_AVAIL=$(top -l 1 | grep "PhysMem" | awk '{print $6}' | tr -d 'M')
        MEM_TOTAL=$((MEM_USAGE + MEM_AVAIL))
        MEM_PERCENT=$((MEM_USAGE * 100 / MEM_TOTAL))
    else
        # Linux
        MEM_TOTAL=$(free -m | grep Mem | awk '{print $2}')
        MEM_AVAIL=$(free -m | grep Mem | awk '{print $7}')
        MEM_PERCENT=$(echo "scale=2; (($MEM_TOTAL-$MEM_AVAIL)*100/$MEM_TOTAL)" | bc)
    fi
    
    # 获取 TCP 连接数
    if [[ "$(uname)" == "Darwin" ]]; then
        # macOS
        TCP_CONN=$(netstat -an | grep -c "ESTABLISHED")
    else
        # Linux
        TCP_CONN=$(netstat -ant | grep -c "ESTABLISHED")
    fi
    
    # 获取文件描述符数
    if [[ "$(uname)" == "Darwin" ]]; then
        # macOS
        FD_COUNT=$(lsof | wc -l | tr -d ' ')
    else
        # Linux
        FD_COUNT=$(lsof | wc -l | tr -d ' ')
    fi
    
    # 写入数据
    echo "$TIMESTAMP,$CPU_USAGE,$MEM_PERCENT,$MEM_AVAIL,$TCP_CONN,$FD_COUNT" >> $OUTPUT_FILE
    
    # 显示当前状态
    echo -ne "监控进度: $i/$ITERATIONS\r"
    
    # 等待下一个间隔
    sleep $INTERVAL
done

echo ""
echo "监控完成，结果保存在 $OUTPUT_FILE"
