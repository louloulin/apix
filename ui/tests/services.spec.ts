import { test, expect } from '@playwright/test';

test.describe('Services API Integration', () => {
  test.beforeEach(async ({ page }) => {
    // 设置模拟响应
    await page.route('**/admin/services', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          services: [
            {
              id: 'auth-service',
              name: 'Authentication Service',
              url: 'http://auth-service:8080',
              protocol: 'http',
              host: 'auth-service',
              port: 8080,
              path: '/',
              retries: 3,
              connectTimeout: 5000,
              readTimeout: 30000,
              enabled: true,
              description: 'Authentication and authorization service'
            },
            {
              id: 'user-service',
              name: 'User Service',
              url: 'http://user-service:8081',
              protocol: 'http',
              host: 'user-service',
              port: 8081,
              path: '/',
              retries: 3,
              connectTimeout: 5000,
              readTimeout: 30000,
              enabled: true,
              description: 'User management service'
            },
            {
              id: 'product-service',
              name: 'Product Service',
              url: 'http://product-service:8082',
              protocol: 'http',
              host: 'product-service',
              port: 8082,
              path: '/',
              retries: 3,
              connectTimeout: 5000,
              readTimeout: 30000,
              enabled: false,
              description: 'Product management service'
            }
          ],
          total: 3,
          page: 1,
          pageSize: 10
        })
      });
    });
  });

  test('should list all services', async ({ page }) => {
    await page.goto('/en/dashboard/services');
    await page.waitForSelector('[data-testid="services-table"]', { timeout: 5000 });
    
    // 验证服务列表是否加载
    const serviceRows = await page.locator('[data-testid="service-row"]').count();
    expect(serviceRows).toBe(3);
    
    // 验证服务数据是否正确显示
    expect(await page.locator('[data-testid="service-row"]:first-child td:first-child').textContent()).toContain('Authentication Service');
  });

  test('should filter services by status', async ({ page }) => {
    await page.goto('/en/dashboard/services');
    await page.waitForSelector('[data-testid="services-table"]', { timeout: 5000 });
    
    // 点击 Enabled 状态过滤器
    await page.selectOption('select', { label: 'Enabled' });
    
    // 设置模拟响应
    await page.route('**/admin/services?page=1&pageSize=10&sortBy=name&sortOrder=asc&enabled=true', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          services: [
            {
              id: 'auth-service',
              name: 'Authentication Service',
              url: 'http://auth-service:8080',
              protocol: 'http',
              host: 'auth-service',
              port: 8080,
              path: '/',
              retries: 3,
              connectTimeout: 5000,
              readTimeout: 30000,
              enabled: true,
              description: 'Authentication and authorization service'
            },
            {
              id: 'user-service',
              name: 'User Service',
              url: 'http://user-service:8081',
              protocol: 'http',
              host: 'user-service',
              port: 8081,
              path: '/',
              retries: 3,
              connectTimeout: 5000,
              readTimeout: 30000,
              enabled: true,
              description: 'User management service'
            }
          ],
          total: 2,
          page: 1,
          pageSize: 10
        })
      });
    });
    
    // 验证只显示启用的服务
    const serviceRows = await page.locator('[data-testid="service-row"]').count();
    expect(serviceRows).toBe(2);
  });

  test('should search services', async ({ page }) => {
    await page.goto('/en/dashboard/services');
    await page.waitForSelector('[data-testid="services-table"]', { timeout: 5000 });
    
    // 输入搜索查询
    await page.fill('[data-testid="service-search"]', 'user');
    
    // 设置模拟响应
    await page.route('**/admin/services?page=1&pageSize=10&sortBy=name&sortOrder=asc&search=user', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          services: [
            {
              id: 'user-service',
              name: 'User Service',
              url: 'http://user-service:8081',
              protocol: 'http',
              host: 'user-service',
              port: 8081,
              path: '/',
              retries: 3,
              connectTimeout: 5000,
              readTimeout: 30000,
              enabled: true,
              description: 'User management service'
            }
          ],
          total: 1,
          page: 1,
          pageSize: 10
        })
      });
    });
    
    // 验证搜索结果
    const serviceRows = await page.locator('[data-testid="service-row"]').count();
    expect(serviceRows).toBe(1);
    expect(await page.locator('[data-testid="service-row"]:first-child td:first-child').textContent()).toContain('User Service');
  });

  test('should toggle service status', async ({ page }) => {
    await page.goto('/en/dashboard/services');
    await page.waitForSelector('[data-testid="services-table"]', { timeout: 5000 });
    
    // 设置模拟响应
    await page.route('**/admin/services/auth-service/disable', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          service: {
            id: 'auth-service',
            name: 'Authentication Service',
            url: 'http://auth-service:8080',
            protocol: 'http',
            host: 'auth-service',
            port: 8080,
            path: '/',
            retries: 3,
            connectTimeout: 5000,
            readTimeout: 30000,
            enabled: false,
            description: 'Authentication and authorization service'
          },
          message: 'Service disabled successfully'
        })
      });
    });
    
    // 点击第一个服务的开关
    await page.click('[data-testid="service-row"]:first-child [role="switch"]');
    
    // 验证成功消息
    await expect(page.locator('.toast-title')).toHaveText('Success');
    await expect(page.locator('.toast-description')).toContainText('has been disabled successfully');
  });

  test('should delete a service', async ({ page }) => {
    await page.goto('/en/dashboard/services');
    await page.waitForSelector('[data-testid="services-table"]', { timeout: 5000 });
    
    // 模拟确认对话框
    page.on('dialog', dialog => dialog.accept());
    
    // 设置模拟响应
    await page.route('**/admin/services/auth-service', async (route) => {
      if (route.request().method() === 'DELETE') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            success: true,
            message: 'Service deleted successfully'
          })
        });
      }
    });
    
    // 点击第一个服务的删除按钮
    await page.click('[data-testid="service-row"]:first-child button:has-text("Delete")');
    
    // 验证成功消息
    await expect(page.locator('.toast-title')).toHaveText('Success');
    await expect(page.locator('.toast-description')).toContainText('has been deleted successfully');
  });

  test('should check service health', async ({ page }) => {
    await page.goto('/en/dashboard/services');
    await page.waitForSelector('[data-testid="services-table"]', { timeout: 5000 });
    
    // 设置模拟响应
    await page.route('**/admin/services/auth-service/health', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          status: 'UP',
          timestamp: Date.now(),
          details: {
            database: 'UP',
            cache: 'UP'
          }
        })
      });
    });
    
    // 点击第一个服务的健康检查按钮
    await page.click('[data-testid="service-row"]:first-child button:has-text("Check Health")');
    
    // 验证成功消息
    await expect(page.locator('.toast-title')).toHaveText('Health Status');
    await expect(page.locator('.toast-description')).toContainText('Healthy');
  });

  test('should navigate to create service page', async ({ page }) => {
    await page.goto('/en/dashboard/services');
    
    // 点击添加服务按钮
    await page.click('button:has-text("Add Service")');
    
    // 验证导航到创建服务页面
    await expect(page).toHaveURL('/en/dashboard/services/create');
  });

  test('should navigate to edit service page', async ({ page }) => {
    await page.goto('/en/dashboard/services');
    await page.waitForSelector('[data-testid="services-table"]', { timeout: 5000 });
    
    // 点击第一个服务的编辑按钮
    await page.click('[data-testid="service-row"]:first-child button:has-text("Edit")');
    
    // 验证导航到编辑服务页面
    await expect(page).toHaveURL('/en/dashboard/services/auth-service/edit');
  });

  test('should handle API errors gracefully', async ({ page }) => {
    // 设置错误响应
    await page.route('**/admin/services', async (route) => {
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({
          error: 'Internal Server Error',
          message: 'Failed to fetch services'
        })
      });
    });
    
    await page.goto('/en/dashboard/services');
    
    // 验证错误消息
    await expect(page.locator('.alert-title')).toHaveText('Error');
    await expect(page.locator('.alert-description')).toContainText('Failed to fetch services');
  });
});
