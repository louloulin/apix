#!/bin/bash

# 脚本用于移除对ManagementFactory的引用

echo "===== 开始移除对ManagementFactory的引用 ====="

# 1. 修改ClusterMonitor.kt
echo "修改ClusterMonitor.kt..."
cp src/main/kotlin/com/louloulin/apix/cluster/ClusterMonitor.kt src/main/kotlin/com/louloulin/apix/cluster/ClusterMonitor.kt.bak
sed -i.bak 's/import java.lang.management.ManagementFactory/\/\/ import java.lang.management.ManagementFactory/' src/main/kotlin/com/louloulin/apix/cluster/ClusterMonitor.kt
sed -i.bak 's/val memoryMXBean = ManagementFactory.getMemoryMXBean()/val runtime = Runtime.getRuntime()/' src/main/kotlin/com/louloulin/apix/cluster/ClusterMonitor.kt
sed -i.bak 's/val threadMXBean = ManagementFactory.getThreadMXBean()/val threadCount = Thread.activeCount()/' src/main/kotlin/com/louloulin/apix/cluster/ClusterMonitor.kt
sed -i.bak 's/memoryMXBean.heapMemoryUsage.used/runtime.totalMemory() - runtime.freeMemory()/' src/main/kotlin/com/louloulin/apix/cluster/ClusterMonitor.kt
sed -i.bak 's/memoryMXBean.heapMemoryUsage.max/runtime.maxMemory()/' src/main/kotlin/com/louloulin/apix/cluster/ClusterMonitor.kt
sed -i.bak 's/threadMXBean.threadCount/threadCount/' src/main/kotlin/com/louloulin/apix/cluster/ClusterMonitor.kt

# 2. 修改MemoryManager.kt
echo "修改MemoryManager.kt..."
cp src/main/kotlin/com/louloulin/apix/core/memory/MemoryManager.kt src/main/kotlin/com/louloulin/apix/core/memory/MemoryManager.kt.bak
sed -i.bak 's/import java.lang.management.ManagementFactory/\/\/ import java.lang.management.ManagementFactory/' src/main/kotlin/com/louloulin/apix/core/memory/MemoryManager.kt
sed -i.bak 's/import java.lang.management.MemoryMXBean/\/\/ import java.lang.management.MemoryMXBean/' src/main/kotlin/com/louloulin/apix/core/memory/MemoryManager.kt
sed -i.bak 's/import java.lang.management.MemoryPoolMXBean/\/\/ import java.lang.management.MemoryPoolMXBean/' src/main/kotlin/com/louloulin/apix/core/memory/MemoryManager.kt
sed -i.bak 's/private val memoryMXBean: MemoryMXBean = ManagementFactory.getMemoryMXBean()/private val runtime = Runtime.getRuntime()/' src/main/kotlin/com/louloulin/apix/core/memory/MemoryManager.kt
sed -i.bak 's/private val memoryPoolMXBeans: List<MemoryPoolMXBean> = ManagementFactory.getMemoryPoolMXBeans()/\/\/ private val memoryPoolMXBeans: List<MemoryPoolMXBean> = ManagementFactory.getMemoryPoolMXBeans()/' src/main/kotlin/com/louloulin/apix/core/memory/MemoryManager.kt
sed -i.bak 's/memoryMXBean.heapMemoryUsage.used/runtime.totalMemory() - runtime.freeMemory()/' src/main/kotlin/com/louloulin/apix/core/memory/MemoryManager.kt
sed -i.bak 's/memoryMXBean.heapMemoryUsage.max/runtime.maxMemory()/' src/main/kotlin/com/louloulin/apix/core/memory/MemoryManager.kt
sed -i.bak 's/memoryMXBean.heapMemoryUsage.committed/runtime.totalMemory()/' src/main/kotlin/com/louloulin/apix/core/memory/MemoryManager.kt
sed -i.bak 's/memoryMXBean.heapMemoryUsage.init/0L/' src/main/kotlin/com/louloulin/apix/core/memory/MemoryManager.kt
sed -i.bak 's/memoryMXBean.nonHeapMemoryUsage.used/0L/' src/main/kotlin/com/louloulin/apix/core/memory/MemoryManager.kt
sed -i.bak 's/memoryMXBean.nonHeapMemoryUsage.max/0L/' src/main/kotlin/com/louloulin/apix/core/memory/MemoryManager.kt
sed -i.bak 's/memoryMXBean.nonHeapMemoryUsage.committed/0L/' src/main/kotlin/com/louloulin/apix/core/memory/MemoryManager.kt
sed -i.bak 's/memoryMXBean.nonHeapMemoryUsage.init/0L/' src/main/kotlin/com/louloulin/apix/core/memory/MemoryManager.kt
sed -i.bak 's/memoryPoolMXBeans.forEach/\/\/ memoryPoolMXBeans.forEach/' src/main/kotlin/com/louloulin/apix/core/memory/MemoryManager.kt

# 3. 修改MonitorVerticle.kt
echo "修改MonitorVerticle.kt..."
cp src/main/kotlin/com/louloulin/apix/core/verticle/MonitorVerticle.kt src/main/kotlin/com/louloulin/apix/core/verticle/MonitorVerticle.kt.bak
sed -i.bak 's/import java.lang.management.ManagementFactory/\/\/ import java.lang.management.ManagementFactory/' src/main/kotlin/com/louloulin/apix/core/verticle/MonitorVerticle.kt
sed -i.bak 's/val memoryMXBean = ManagementFactory.getMemoryMXBean()/val runtime = Runtime.getRuntime()/' src/main/kotlin/com/louloulin/apix/core/verticle/MonitorVerticle.kt
sed -i.bak 's/val threadMXBean = ManagementFactory.getThreadMXBean()/val threadCount = Thread.activeCount()/' src/main/kotlin/com/louloulin/apix/core/verticle/MonitorVerticle.kt
sed -i.bak 's/memoryMXBean.heapMemoryUsage.used/runtime.totalMemory() - runtime.freeMemory()/' src/main/kotlin/com/louloulin/apix/core/verticle/MonitorVerticle.kt
sed -i.bak 's/memoryMXBean.heapMemoryUsage.max/runtime.maxMemory()/' src/main/kotlin/com/louloulin/apix/core/verticle/MonitorVerticle.kt
sed -i.bak 's/threadMXBean.threadCount/threadCount/' src/main/kotlin/com/louloulin/apix/core/verticle/MonitorVerticle.kt
sed -i.bak 's/.put("uptime", ManagementFactory.getRuntimeMXBean().uptime)/.put("uptime", System.currentTimeMillis() - startTime)/' src/main/kotlin/com/louloulin/apix/core/verticle/MonitorVerticle.kt

# 4. 修改HealthVerticle.kt
echo "修改HealthVerticle.kt..."
cp src/main/kotlin/com/louloulin/apix/core/verticle/HealthVerticle.kt src/main/kotlin/com/louloulin/apix/core/verticle/HealthVerticle.kt.bak
sed -i.bak 's/import java.lang.management.ManagementFactory/\/\/ import java.lang.management.ManagementFactory/' src/main/kotlin/com/louloulin/apix/core/verticle/HealthVerticle.kt
sed -i.bak 's/val memoryMXBean = ManagementFactory.getMemoryMXBean()/val runtime = Runtime.getRuntime()/' src/main/kotlin/com/louloulin/apix/core/verticle/HealthVerticle.kt
sed -i.bak 's/val osMXBean = ManagementFactory.getOperatingSystemMXBean()/val availableProcessors = Runtime.getRuntime().availableProcessors()/' src/main/kotlin/com/louloulin/apix/core/verticle/HealthVerticle.kt
sed -i.bak 's/val runtimeMXBean = ManagementFactory.getRuntimeMXBean()/val startTime = System.currentTimeMillis()/' src/main/kotlin/com/louloulin/apix/core/verticle/HealthVerticle.kt
sed -i.bak 's/memoryMXBean.heapMemoryUsage.used/runtime.totalMemory() - runtime.freeMemory()/' src/main/kotlin/com/louloulin/apix/core/verticle/HealthVerticle.kt
sed -i.bak 's/memoryMXBean.heapMemoryUsage.max/runtime.maxMemory()/' src/main/kotlin/com/louloulin/apix/core/verticle/HealthVerticle.kt
sed -i.bak 's/osMXBean.availableProcessors/availableProcessors/' src/main/kotlin/com/louloulin/apix/core/verticle/HealthVerticle.kt
sed -i.bak 's/runtimeMXBean.uptime/System.currentTimeMillis() - startTime/' src/main/kotlin/com/louloulin/apix/core/verticle/HealthVerticle.kt

# 5. 修改ConcurrencyController.kt
echo "修改ConcurrencyController.kt..."
cp src/main/kotlin/com/louloulin/apix/core/concurrency/ConcurrencyController.kt src/main/kotlin/com/louloulin/apix/core/concurrency/ConcurrencyController.kt.bak
sed -i.bak 's/val osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean()/val availableProcessors = Runtime.getRuntime().availableProcessors()/' src/main/kotlin/com/louloulin/apix/core/concurrency/ConcurrencyController.kt
sed -i.bak 's/osBean.availableProcessors/availableProcessors/' src/main/kotlin/com/louloulin/apix/core/concurrency/ConcurrencyController.kt

# 6. 修改LoggingManager.kt
echo "修改LoggingManager.kt..."
cp src/main/kotlin/com/louloulin/apix/core/logging/LoggingManager.kt src/main/kotlin/com/louloulin/apix/core/logging/LoggingManager.kt.bak
sed -i.bak 's/val osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean()/val availableProcessors = Runtime.getRuntime().availableProcessors()/' src/main/kotlin/com/louloulin/apix/core/logging/LoggingManager.kt
sed -i.bak 's/osBean.availableProcessors/availableProcessors/' src/main/kotlin/com/louloulin/apix/core/logging/LoggingManager.kt

# 7. 修改reflect-config.json，移除ManagementFactory相关配置
echo "修改reflect-config.json，移除ManagementFactory相关配置..."
cp src/main/resources/META-INF/native-image/reflect-config.json src/main/resources/META-INF/native-image/reflect-config.json.bak
sed -i.bak '/ManagementFactory/d' src/main/resources/META-INF/native-image/reflect-config.json

echo "===== 移除对ManagementFactory的引用完成 ====="
