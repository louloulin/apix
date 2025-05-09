import { test, expect } from '@playwright/test';

test.describe('Metrics API Integration', () => {
  test.beforeEach(async ({ page }) => {
    // 设置模拟响应
    await page.route('**/admin/metrics', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          timestamp: Date.now(),
          uptime: 3600000, // 1 hour in milliseconds
          requestCount: 15000,
          activeConnections: 120,
          requestsPerSecond: 42.5,
          averageResponseTime: 15.3,
          errorRate: 0.02,
          cpu: {
            systemCpuLoad: 0.45,
            processCpuLoad: 0.23,
            availableProcessors: 8,
            systemLoadAverage: 2.15,
            processCpuTime: 25000000000,
            cores: [
              { coreId: 0, usage: 0.35 },
              { coreId: 1, usage: 0.42 },
              { coreId: 2, usage: 0.18 },
              { coreId: 3, usage: 0.56 },
              { coreId: 4, usage: 0.22 },
              { coreId: 5, usage: 0.31 },
              { coreId: 6, usage: 0.27 },
              { coreId: 7, usage: 0.19 }
            ]
          },
          memory: {
            heapMemoryUsed: 187699728,
            heapMemoryMax: 4294967296,
            heapMemoryCommitted: 268435456,
            nonHeapMemoryUsed: 125829120,
            nonHeapMemoryCommitted: 134217728,
            systemMemoryTotal: 17179869184,
            systemMemoryFree: 8589934592,
            systemMemoryUsed: 8589934592,
            memoryPools: [
              { name: 'Eden Space', used: 67108864, max: 1073741824, committed: 134217728 },
              { name: 'Survivor Space', used: 8388608, max: 134217728, committed: 16777216 },
              { name: 'Old Gen', used: 112202256, max: 2147483648, committed: 134217728 }
            ]
          },
          threads: {
            threadCount: 32,
            daemonThreadCount: 28,
            peakThreadCount: 36,
            totalStartedThreadCount: 42,
            deadlockedThreads: 0,
            threadStates: [
              { state: 'RUNNABLE', count: 12 },
              { state: 'WAITING', count: 8 },
              { state: 'TIMED_WAITING', count: 10 },
              { state: 'BLOCKED', count: 2 }
            ]
          },
          jvm: {
            jvmName: 'OpenJDK 64-Bit Server VM',
            jvmVersion: '17.0.6+10-LTS',
            jvmVendor: 'GraalVM Community',
            startTime: Date.now() - 3600000, // 1 hour ago
            uptime: 3600000, // 1 hour in milliseconds
            gcCollectors: [
              { name: 'G1 Young Generation', collectionCount: 12, collectionTime: 235 },
              { name: 'G1 Old Generation', collectionCount: 2, collectionTime: 350 }
            ],
            classLoading: {
              loadedClassCount: 12500,
              totalLoadedClassCount: 12800,
              unloadedClassCount: 300
            }
          },
          os: {
            name: 'Linux',
            version: '5.15.0-1031-aws',
            arch: 'amd64',
            availableProcessors: 8,
            systemLoadAverage: 2.15,
            committedVirtualMemory: 4294967296,
            totalSwapSpace: 2147483648,
            freeSwapSpace: 1073741824,
            totalPhysicalMemory: 17179869184,
            freePhysicalMemory: 8589934592,
            fileDescriptors: {
              open: 256,
              max: 65536
            }
          }
        })
      });
    });
    
    await page.route('**/admin/health', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          status: 'UP',
          timestamp: Date.now(),
          components: [
            { name: 'database', status: 'UP' },
            { name: 'diskSpace', status: 'UP' },
            { name: 'redis', status: 'UP' }
          ]
        })
      });
    });
  });

  test('should load metrics page', async ({ page }) => {
    await page.goto('/en/dashboard/metrics');
    
    // 等待页面加载完成
    await page.waitForSelector('[data-testid="metrics-page"]', { timeout: 5000 });
    
    // 验证页面标题
    expect(await page.textContent('h1')).toBe('Metrics');
    
    // 验证健康状态
    const healthStatus = await page.locator('.badge').first();
    expect(await healthStatus.textContent()).toBe('Healthy');
    
    // 验证 CPU 使用率
    const cpuUsage = await page.locator('text=CPU Usage').first().locator('xpath=../..').locator('.text-2xl');
    expect(await cpuUsage.textContent()).toContain('23');
    
    // 验证内存使用率
    const memoryUsage = await page.locator('text=Memory Usage').first().locator('xpath=../..').locator('.text-2xl');
    expect(await memoryUsage.textContent()).toContain('4.37');
    
    // 验证线程数
    const threadCount = await page.locator('text=Thread Count').first().locator('xpath=../..').locator('.text-2xl');
    expect(await threadCount.textContent()).toBe('32');
  });

  test('should navigate between tabs', async ({ page }) => {
    await page.goto('/en/dashboard/metrics');
    await page.waitForSelector('[data-testid="metrics-page"]', { timeout: 5000 });
    
    // 点击 CPU 标签
    await page.click('button:has-text("CPU")');
    
    // 验证 CPU 标签内容
    expect(await page.isVisible('text=Process CPU Load')).toBe(true);
    expect(await page.isVisible('text=System CPU Load')).toBe(true);
    
    // 点击内存标签
    await page.click('button:has-text("Memory")');
    
    // 验证内存标签内容
    expect(await page.isVisible('text=Heap Memory')).toBe(true);
    expect(await page.isVisible('text=Non-Heap Memory')).toBe(true);
    
    // 点击线程标签
    await page.click('button:has-text("Threads")');
    
    // 验证线程标签内容
    expect(await page.isVisible('text=Thread Count')).toBe(true);
    expect(await page.isVisible('text=Daemon Threads')).toBe(true);
    
    // 点击 JVM 标签
    await page.click('button:has-text("JVM")');
    
    // 验证 JVM 标签内容
    expect(await page.isVisible('text=JVM Information')).toBe(true);
    expect(await page.isVisible('text=JVM Uptime')).toBe(true);
  });

  test('should refresh metrics', async ({ page }) => {
    // 设置刷新后的模拟响应
    await page.route('**/admin/metrics', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          timestamp: Date.now(),
          uptime: 3660000, // 1 hour and 1 minute in milliseconds
          requestCount: 16000, // Increased from 15000
          activeConnections: 130, // Increased from 120
          requestsPerSecond: 45.2, // Increased from 42.5
          averageResponseTime: 14.8, // Decreased from 15.3
          errorRate: 0.018, // Decreased from 0.02
          cpu: {
            systemCpuLoad: 0.48, // Increased from 0.45
            processCpuLoad: 0.25, // Increased from 0.23
            availableProcessors: 8,
            systemLoadAverage: 2.25, // Increased from 2.15
            processCpuTime: 26000000000, // Increased from 25000000000
            cores: [
              { coreId: 0, usage: 0.38 },
              { coreId: 1, usage: 0.45 },
              { coreId: 2, usage: 0.20 },
              { coreId: 3, usage: 0.58 },
              { coreId: 4, usage: 0.24 },
              { coreId: 5, usage: 0.33 },
              { coreId: 6, usage: 0.29 },
              { coreId: 7, usage: 0.21 }
            ]
          },
          memory: {
            heapMemoryUsed: 195000000, // Increased from 187699728
            heapMemoryMax: 4294967296,
            heapMemoryCommitted: 268435456,
            nonHeapMemoryUsed: 128000000, // Increased from 125829120
            nonHeapMemoryCommitted: 134217728,
            systemMemoryTotal: 17179869184,
            systemMemoryFree: 8389934592, // Decreased from 8589934592
            systemMemoryUsed: 8789934592, // Increased from 8589934592
            memoryPools: [
              { name: 'Eden Space', used: 70000000, max: 1073741824, committed: 134217728 },
              { name: 'Survivor Space', used: 9000000, max: 134217728, committed: 16777216 },
              { name: 'Old Gen', used: 116000000, max: 2147483648, committed: 134217728 }
            ]
          },
          threads: {
            threadCount: 34, // Increased from 32
            daemonThreadCount: 30, // Increased from 28
            peakThreadCount: 36,
            totalStartedThreadCount: 44, // Increased from 42
            deadlockedThreads: 0,
            threadStates: [
              { state: 'RUNNABLE', count: 14 },
              { state: 'WAITING', count: 8 },
              { state: 'TIMED_WAITING', count: 10 },
              { state: 'BLOCKED', count: 2 }
            ]
          },
          jvm: {
            jvmName: 'OpenJDK 64-Bit Server VM',
            jvmVersion: '17.0.6+10-LTS',
            jvmVendor: 'GraalVM Community',
            startTime: Date.now() - 3660000, // 1 hour and 1 minute ago
            uptime: 3660000, // 1 hour and 1 minute in milliseconds
            gcCollectors: [
              { name: 'G1 Young Generation', collectionCount: 13, collectionTime: 245 },
              { name: 'G1 Old Generation', collectionCount: 2, collectionTime: 350 }
            ],
            classLoading: {
              loadedClassCount: 12550,
              totalLoadedClassCount: 12850,
              unloadedClassCount: 300
            }
          },
          os: {
            name: 'Linux',
            version: '5.15.0-1031-aws',
            arch: 'amd64',
            availableProcessors: 8,
            systemLoadAverage: 2.25,
            committedVirtualMemory: 4294967296,
            totalSwapSpace: 2147483648,
            freeSwapSpace: 1073741824,
            totalPhysicalMemory: 17179869184,
            freePhysicalMemory: 8389934592,
            fileDescriptors: {
              open: 260,
              max: 65536
            }
          }
        })
      });
    }, { times: 1 });
    
    await page.goto('/en/dashboard/metrics');
    await page.waitForSelector('[data-testid="metrics-page"]', { timeout: 5000 });
    
    // 记录初始 CPU 使用率
    const initialCpuUsage = await page.locator('text=CPU Usage').first().locator('xpath=../..').locator('.text-2xl').textContent();
    
    // 点击刷新按钮
    await page.click('button:has-text("Refresh")');
    
    // 等待刷新完成
    await page.waitForSelector(':not(.animate-spin)', { timeout: 5000 });
    
    // 验证 CPU 使用率已更新
    const updatedCpuUsage = await page.locator('text=CPU Usage').first().locator('xpath=../..').locator('.text-2xl').textContent();
    expect(updatedCpuUsage).not.toBe(initialCpuUsage);
    expect(updatedCpuUsage).toContain('25');
  });

  test('should handle API errors gracefully', async ({ page }) => {
    // 设置错误响应
    await page.route('**/admin/metrics', async (route) => {
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({
          error: 'Internal Server Error',
          message: 'Failed to fetch metrics'
        })
      });
    });
    
    await page.goto('/en/dashboard/metrics');
    
    // 验证错误消息
    await expect(page.locator('.toast-title')).toHaveText('Error');
    await expect(page.locator('.toast-description')).toContainText('Failed to fetch metrics');
  });
});
