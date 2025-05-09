import { test, expect } from '@playwright/test';

// 配置管理 API 测试
test.describe('Configuration API Integration', () => {
  test.beforeEach(async ({ page }) => {
    // 设置模拟响应
    await page.route('**/admin/config', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            config: {
              gateway: {
                host: '0.0.0.0',
                port: 8080,
                ssl: {
                  enabled: false,
                  certPath: '',
                  keyPath: ''
                }
              },
              admin: {
                enabled: true,
                host: '0.0.0.0',
                port: 9090
              },
              logging: {
                level: 'INFO',
                file: '/var/log/apix.log',
                console: true
              }
            }
          })
        });
      }
    });
  });

  test('should load configuration page', async ({ page }) => {
    await page.goto('/dashboard/config');

    // 验证页面标题
    await expect(page.locator('h1')).toContainText('Configuration');

    // 验证配置是否加载
    await expect(page.getByText('Gateway').first()).toBeVisible();
    await expect(page.getByText('Admin').first()).toBeVisible();
  });
});

// 系统指标 API 测试
test.describe('Metrics API Integration', () => {
  test.beforeEach(async ({ page }) => {
    // 设置模拟响应
    await page.route('**/admin/metrics', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          timestamp: Date.now(),
          uptime: 3600000,
          requestCount: 15000,
          activeConnections: 120,
          requestsPerSecond: 42.5,
          averageResponseTime: 15.3,
          errorRate: 0.02,
          cpu: {
            systemCpuLoad: 0.45,
            processCpuLoad: 0.23,
            availableProcessors: 8,
            systemLoadAverage: 2.15
          },
          memory: {
            heapMemoryUsed: 187699728,
            heapMemoryMax: 4294967296,
            heapMemoryCommitted: 268435456,
            nonHeapMemoryUsed: 125829120,
            nonHeapMemoryCommitted: 134217728,
            systemMemoryTotal: 17179869184,
            systemMemoryFree: 8589934592,
            systemMemoryUsed: 8589934592
          },
          threads: {
            threadCount: 32,
            daemonThreadCount: 28,
            peakThreadCount: 36,
            totalStartedThreadCount: 42,
            deadlockedThreads: 0
          },
          jvm: {
            jvmName: 'OpenJDK 64-Bit Server VM',
            jvmVersion: '17.0.6+10-LTS',
            jvmVendor: 'GraalVM Community',
            startTime: Date.now() - 3600000,
            uptime: 3600000
          },
          os: {
            name: 'Linux',
            version: '5.15.0-1031-aws',
            arch: 'amd64',
            availableProcessors: 8,
            systemLoadAverage: 2.15
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
    await page.goto('/dashboard/metrics');

    // 验证页面标题
    await expect(page.locator('h1')).toContainText('Metrics');

    // 验证指标是否加载
    await expect(page.getByText('CPU Usage').first()).toBeVisible();
    await expect(page.getByText('Memory Usage').first()).toBeVisible();
    await expect(page.getByText('Thread Count').first()).toBeVisible();
  });
});

// AI 模型管理 API 测试
test.describe('AI Models API Integration', () => {
  test.beforeEach(async ({ page }) => {
    // 设置模拟响应
    await page.route('**/admin/ai/models', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          models: [
            {
              id: 'gpt-4',
              name: 'GPT-4',
              provider: 'OpenAI',
              description: 'OpenAI GPT-4 model',
              maxTokens: 8192,
              enabled: true,
              priority: 1,
              costPerToken: 0.00006,
              capabilities: ['text-generation', 'chat', 'embeddings']
            },
            {
              id: 'gpt-3.5-turbo',
              name: 'GPT-3.5 Turbo',
              provider: 'OpenAI',
              description: 'OpenAI GPT-3.5 Turbo model',
              maxTokens: 4096,
              enabled: true,
              priority: 2,
              costPerToken: 0.00002,
              capabilities: ['text-generation', 'chat']
            }
          ],
          total: 2,
          page: 1,
          pageSize: 10
        })
      });
    });
  });

  test('should load AI models page', async ({ page }) => {
    await page.goto('/dashboard/ai/models');

    // 验证页面标题
    await expect(page.locator('h1')).toContainText('AI Models');

    // 验证模型列表是否加载
    await expect(page.locator('text=GPT-4')).toBeVisible();
    await expect(page.locator('text=GPT-3.5 Turbo')).toBeVisible();
  });
});

// AI 路由规则 API 测试
test.describe('AI Routing Rules API Integration', () => {
  test.beforeEach(async ({ page }) => {
    // 设置模拟响应
    await page.route('**/admin/ai/routing/rules', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          rules: [
            {
              id: 'rule-1',
              name: 'Image Processing Rule',
              priority: 1,
              condition: {
                type: 'content',
                pattern: 'image/*',
                contentTypes: ['image/jpeg', 'image/png', 'image/gif']
              },
              targetModel: 'gpt-4',
              enabled: true
            },
            {
              id: 'rule-2',
              name: 'API Requests Rule',
              priority: 2,
              condition: {
                type: 'path',
                pattern: '/api/*'
              },
              targetModel: 'gpt-3.5-turbo',
              enabled: true
            }
          ],
          total: 2,
          page: 1,
          pageSize: 10
        })
      });
    });

    await page.route('**/admin/ai/models?enabled=true', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          models: [
            {
              id: 'gpt-4',
              name: 'GPT-4',
              provider: 'OpenAI',
              enabled: true
            },
            {
              id: 'gpt-3.5-turbo',
              name: 'GPT-3.5 Turbo',
              provider: 'OpenAI',
              enabled: true
            }
          ],
          total: 2,
          page: 1,
          pageSize: 10
        })
      });
    });
  });

  test('should load AI routing rules page', async ({ page }) => {
    await page.goto('/dashboard/ai/routing');

    // 验证页面标题
    await expect(page.locator('h1')).toContainText('AI Routing Rules');

    // 验证规则列表是否加载
    await expect(page.locator('text=Image Processing Rule')).toBeVisible();
    await expect(page.locator('text=API Requests Rule')).toBeVisible();
  });
});

// API 密钥管理 API 测试
test.describe('API Keys Management API Integration', () => {
  test.beforeEach(async ({ page }) => {
    // 设置模拟响应
    await page.route('**/admin/auth/api-keys', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            keys: [
              {
                id: 'key-1',
                key: 'apix.123456789abcdef',
                name: 'Test API Key',
                scopes: ['read:metrics', 'read:config'],
                enabled: true,
                createdAt: '2023-07-01T12:00:00Z',
                expiresAt: '2024-07-01T12:00:00Z',
                lastUsedAt: '2023-07-10T15:30:00Z',
                description: 'API key for testing'
              },
              {
                id: 'key-2',
                key: 'apix.abcdefghijklmno',
                name: 'Production API Key',
                scopes: ['read:metrics', 'write:config', 'read:routes', 'write:routes'],
                enabled: true,
                createdAt: '2023-06-15T10:00:00Z',
                description: 'API key for production use'
              }
            ],
            total: 2,
            page: 1,
            pageSize: 10
          })
        });
      }
    });

    await page.route('**/admin/auth/api-keys/scopes', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          scopes: [
            { id: 'read:metrics', name: 'Read Metrics', description: 'Access to read system metrics' },
            { id: 'read:config', name: 'Read Config', description: 'Access to read system configuration' },
            { id: 'write:config', name: 'Write Config', description: 'Access to modify system configuration' },
            { id: 'read:routes', name: 'Read Routes', description: 'Access to read route information' },
            { id: 'write:routes', name: 'Write Routes', description: 'Access to modify routes' }
          ]
        })
      });
    });
  });

  test('should load API keys page', async ({ page }) => {
    await page.goto('/dashboard/api-keys');

    // 验证页面标题
    await expect(page.locator('h1')).toContainText('API Keys');

    // 验证页面是否加载
    await expect(page.getByText('Create API Key')).toBeVisible();
  });
});
