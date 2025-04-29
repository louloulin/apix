#!/bin/bash

# 优化系统配置以支持高并发连接
# 注意：此脚本需要 root 权限执行

# 显示当前系统限制
echo "当前系统限制："
ulimit -a
echo ""

# 临时增加文件描述符限制
echo "临时增加文件描述符限制..."
ulimit -n 65535
echo "新的文件描述符限制："
ulimit -n
echo ""

# 优化内核参数
echo "优化内核参数..."

# 检查是否为 Linux 系统
if [ "$(uname)" == "Linux" ]; then
    # 备份当前 sysctl 配置
    cp /etc/sysctl.conf /etc/sysctl.conf.bak
    
    # 添加优化参数
    cat << EOF >> /etc/sysctl.conf
# 优化网络性能
net.core.somaxconn = 65535
net.core.netdev_max_backlog = 65535
net.ipv4.tcp_max_syn_backlog = 65535
net.ipv4.tcp_fin_timeout = 30
net.ipv4.tcp_keepalive_time = 300
net.ipv4.tcp_keepalive_probes = 5
net.ipv4.tcp_keepalive_intvl = 15
net.ipv4.tcp_tw_reuse = 1
net.ipv4.ip_local_port_range = 1024 65535
net.core.rmem_max = 16777216
net.core.wmem_max = 16777216
net.ipv4.tcp_rmem = 4096 87380 16777216
net.ipv4.tcp_wmem = 4096 65536 16777216
EOF
    
    # 应用新的内核参数
    sysctl -p
    
    echo "内核参数已优化"
elif [ "$(uname)" == "Darwin" ]; then
    echo "MacOS 系统，跳过内核参数优化"
else
    echo "不支持的操作系统"
fi

echo ""
echo "系统优化完成"
