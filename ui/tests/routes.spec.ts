import { test, expect } from '@playwright/test';

test.describe('Routes API Integration', () => {
  test.beforeEach(async ({ page }) => {
    // 设置模拟响应
    await page.route('**/admin/routes', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          routes: [
            {
              id: '1',
              path: '/v1/completions',
              targetUrl: 'https://api.openai.com/v1/completions',
              methods: ['POST'],
              plugins: ['rate-limiter', 'cors'],
              enabled: true,
              priority: 100,
              type: 'llm',
              description: 'OpenAI completions endpoint'
            },
            {
              id: '2',
              path: '/v1/chat/completions',
              targetUrl: 'https://api.openai.com/v1/chat/completions',
              methods: ['POST'],
              plugins: ['rate-limiter', 'cors'],
              enabled: true,
              priority: 100,
              type: 'llm',
              description: 'OpenAI chat completions endpoint'
            },
            {
              id: '3',
              path: '/vectors/search',
              targetUrl: 'INTERNAL',
              methods: ['POST', 'GET'],
              plugins: ['auth'],
              enabled: false,
              priority: 200,
              type: 'vector',
              description: 'Vector search endpoint'
            }
          ],
          total: 3,
          page: 1,
          pageSize: 10
        })
      });
    });
  });

  test('should list all routes', async ({ page }) => {
    await page.goto('/en/dashboard/routes');
    await page.waitForSelector('[data-testid="routes-table"]', { timeout: 5000 });
    
    // 验证路由列表是否加载
    const routeRows = await page.locator('tbody tr').count();
    expect(routeRows).toBe(3);
    
    // 验证路由数据是否正确显示
    expect(await page.locator('tbody tr:first-child td:first-child').textContent()).toContain('/v1/completions');
    expect(await page.locator('tbody tr:first-child td:nth-child(2)').textContent()).toContain('https://api.openai.com/v1/completions');
  });

  test('should filter routes by type', async ({ page }) => {
    await page.goto('/en/dashboard/routes');
    await page.waitForSelector('[data-testid="routes-table"]', { timeout: 5000 });
    
    // 点击 LLM 路由标签
    await page.click('button:has-text("LLM Routes")');
    
    // 设置模拟响应
    await page.route('**/admin/routes?page=1&pageSize=10&sortBy=path&sortOrder=asc&type=llm', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          routes: [
            {
              id: '1',
              path: '/v1/completions',
              targetUrl: 'https://api.openai.com/v1/completions',
              methods: ['POST'],
              plugins: ['rate-limiter', 'cors'],
              enabled: true,
              priority: 100,
              type: 'llm',
              description: 'OpenAI completions endpoint'
            },
            {
              id: '2',
              path: '/v1/chat/completions',
              targetUrl: 'https://api.openai.com/v1/chat/completions',
              methods: ['POST'],
              plugins: ['rate-limiter', 'cors'],
              enabled: true,
              priority: 100,
              type: 'llm',
              description: 'OpenAI chat completions endpoint'
            }
          ],
          total: 2,
          page: 1,
          pageSize: 10
        })
      });
    });
    
    // 验证只显示 LLM 路由
    const routeRows = await page.locator('tbody tr').count();
    expect(routeRows).toBe(2);
  });

  test('should search routes', async ({ page }) => {
    await page.goto('/en/dashboard/routes');
    await page.waitForSelector('[data-testid="routes-table"]', { timeout: 5000 });
    
    // 输入搜索查询
    await page.fill('input[placeholder*="Search"]', 'vector');
    
    // 设置模拟响应
    await page.route('**/admin/routes?page=1&pageSize=10&sortBy=path&sortOrder=asc&search=vector', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          routes: [
            {
              id: '3',
              path: '/vectors/search',
              targetUrl: 'INTERNAL',
              methods: ['POST', 'GET'],
              plugins: ['auth'],
              enabled: false,
              priority: 200,
              type: 'vector',
              description: 'Vector search endpoint'
            }
          ],
          total: 1,
          page: 1,
          pageSize: 10
        })
      });
    });
    
    // 验证搜索结果
    const routeRows = await page.locator('tbody tr').count();
    expect(routeRows).toBe(1);
    expect(await page.locator('tbody tr:first-child td:first-child').textContent()).toContain('/vectors/search');
  });

  test('should toggle route status', async ({ page }) => {
    await page.goto('/en/dashboard/routes');
    await page.waitForSelector('[data-testid="routes-table"]', { timeout: 5000 });
    
    // 设置模拟响应
    await page.route('**/admin/routes/1/disable', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          route: {
            id: '1',
            path: '/v1/completions',
            targetUrl: 'https://api.openai.com/v1/completions',
            methods: ['POST'],
            plugins: ['rate-limiter', 'cors'],
            enabled: false,
            priority: 100,
            type: 'llm',
            description: 'OpenAI completions endpoint'
          },
          message: 'Route disabled successfully'
        })
      });
    });
    
    // 点击第一个路由的开关
    await page.click('tbody tr:first-child [role="switch"]');
    
    // 验证成功消息
    await expect(page.locator('.toast-title')).toHaveText('Success');
    await expect(page.locator('.toast-description')).toContainText('Route Disabled');
  });

  test('should delete a route', async ({ page }) => {
    await page.goto('/en/dashboard/routes');
    await page.waitForSelector('[data-testid="routes-table"]', { timeout: 5000 });
    
    // 模拟确认对话框
    page.on('dialog', dialog => dialog.accept());
    
    // 设置模拟响应
    await page.route('**/admin/routes/1', async (route) => {
      if (route.request().method() === 'DELETE') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            success: true,
            message: 'Route deleted successfully'
          })
        });
      }
    });
    
    // 点击第一个路由的删除按钮
    await page.click('tbody tr:first-child button:has-text("Delete")');
    
    // 验证成功消息
    await expect(page.locator('.toast-title')).toHaveText('Success');
    await expect(page.locator('.toast-description')).toContainText('Route Deleted');
  });

  test('should navigate to create route page', async ({ page }) => {
    await page.goto('/en/dashboard/routes');
    
    // 点击添加路由按钮
    await page.click('button:has-text("Add Route")');
    
    // 验证导航到创建路由页面
    await expect(page).toHaveURL('/en/dashboard/routes/create');
  });

  test('should navigate to edit route page', async ({ page }) => {
    await page.goto('/en/dashboard/routes');
    await page.waitForSelector('[data-testid="routes-table"]', { timeout: 5000 });
    
    // 点击第一个路由的编辑按钮
    await page.click('tbody tr:first-child button:has-text("Edit")');
    
    // 验证导航到编辑路由页面
    await expect(page).toHaveURL('/en/dashboard/routes/1/edit');
  });

  test('should handle API errors gracefully', async ({ page }) => {
    // 设置错误响应
    await page.route('**/admin/routes', async (route) => {
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({
          error: 'Internal Server Error',
          message: 'Failed to fetch routes'
        })
      });
    });
    
    await page.goto('/en/dashboard/routes');
    
    // 验证错误消息
    await expect(page.locator('.alert-title')).toHaveText('Error');
    await expect(page.locator('.alert-description')).toContainText('Failed to fetch routes');
  });
});
