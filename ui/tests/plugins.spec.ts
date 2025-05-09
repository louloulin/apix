import { test, expect } from '@playwright/test';

test.describe('Plugins API Integration', () => {
  test.beforeEach(async ({ page }) => {
    // 设置模拟响应
    await page.route('**/admin/plugins', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          plugins: [
            {
              id: 'jwt-auth',
              type: 'authentication',
              status: 'enabled',
              version: '1.0.0',
              config: { secret: 'test-secret' }
            },
            {
              id: 'rate-limiter',
              type: 'security',
              status: 'enabled',
              version: '1.0.0',
              config: { limit: 100, window: 60 }
            },
            {
              id: 'cors',
              type: 'security',
              status: 'disabled',
              version: '1.0.0',
              config: { origins: ['*'] }
            }
          ],
          total: 3,
          page: 1,
          pageSize: 10
        })
      });
    });
  });

  test('should list all plugins', async ({ page }) => {
    await page.goto('/en/dashboard/plugins');
    await page.waitForSelector('[data-testid="plugins-table"]', { timeout: 5000 });
    
    // 验证插件列表是否加载
    const pluginRows = await page.locator('[data-testid="plugin-row"]').count();
    expect(pluginRows).toBe(3);
    
    // 验证插件数据是否正确显示
    expect(await page.locator('[data-testid="plugin-row"]:first-child td:first-child').textContent()).toContain('jwt-auth');
  });

  test('should filter plugins by type', async ({ page }) => {
    await page.goto('/en/dashboard/plugins');
    await page.waitForSelector('[data-testid="plugins-table"]', { timeout: 5000 });
    
    // 点击 Security 插件标签
    await page.click('button:has-text("Security")');
    
    // 设置模拟响应
    await page.route('**/admin/plugins?page=1&pageSize=10&sortBy=id&sortOrder=asc&type=security', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          plugins: [
            {
              id: 'rate-limiter',
              type: 'security',
              status: 'enabled',
              version: '1.0.0',
              config: { limit: 100, window: 60 }
            },
            {
              id: 'cors',
              type: 'security',
              status: 'disabled',
              version: '1.0.0',
              config: { origins: ['*'] }
            }
          ],
          total: 2,
          page: 1,
          pageSize: 10
        })
      });
    });
    
    // 验证只显示 Security 插件
    const pluginRows = await page.locator('[data-testid="plugin-row"]').count();
    expect(pluginRows).toBe(2);
  });

  test('should search plugins', async ({ page }) => {
    await page.goto('/en/dashboard/plugins');
    await page.waitForSelector('[data-testid="plugins-table"]', { timeout: 5000 });
    
    // 输入搜索查询
    await page.fill('[data-testid="plugin-search"]', 'jwt');
    
    // 设置模拟响应
    await page.route('**/admin/plugins?page=1&pageSize=10&sortBy=id&sortOrder=asc&search=jwt', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          plugins: [
            {
              id: 'jwt-auth',
              type: 'authentication',
              status: 'enabled',
              version: '1.0.0',
              config: { secret: 'test-secret' }
            }
          ],
          total: 1,
          page: 1,
          pageSize: 10
        })
      });
    });
    
    // 验证搜索结果
    const pluginRows = await page.locator('[data-testid="plugin-row"]').count();
    expect(pluginRows).toBe(1);
    expect(await page.locator('[data-testid="plugin-row"]:first-child td:first-child').textContent()).toContain('jwt-auth');
  });

  test('should toggle plugin status', async ({ page }) => {
    await page.goto('/en/dashboard/plugins');
    await page.waitForSelector('[data-testid="plugins-table"]', { timeout: 5000 });
    
    // 设置模拟响应
    await page.route('**/admin/plugins/jwt-auth/disable', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          plugin: {
            id: 'jwt-auth',
            type: 'authentication',
            status: 'disabled',
            version: '1.0.0',
            config: { secret: 'test-secret' }
          },
          message: 'Plugin disabled successfully'
        })
      });
    });
    
    // 点击第一个插件的开关
    await page.click('[data-testid="plugin-row"]:first-child [role="switch"]');
    
    // 验证成功消息
    await expect(page.locator('.toast-title')).toHaveText('Success');
    await expect(page.locator('.toast-description')).toContainText('has been disabled successfully');
  });

  test('should delete a plugin', async ({ page }) => {
    await page.goto('/en/dashboard/plugins');
    await page.waitForSelector('[data-testid="plugins-table"]', { timeout: 5000 });
    
    // 模拟确认对话框
    page.on('dialog', dialog => dialog.accept());
    
    // 设置模拟响应
    await page.route('**/admin/plugins/jwt-auth', async (route) => {
      if (route.request().method() === 'DELETE') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            success: true,
            message: 'Plugin deleted successfully'
          })
        });
      }
    });
    
    // 点击第一个插件的删除按钮
    await page.click('[data-testid="plugin-row"]:first-child button:has-text("Delete")');
    
    // 验证成功消息
    await expect(page.locator('.toast-title')).toHaveText('Success');
    await expect(page.locator('.toast-description')).toContainText('has been deleted successfully');
  });

  test('should navigate to create plugin page', async ({ page }) => {
    await page.goto('/en/dashboard/plugins');
    
    // 点击添加插件按钮
    await page.click('button:has-text("Add Plugin")');
    
    // 验证导航到创建插件页面
    await expect(page).toHaveURL('/en/dashboard/plugins/create');
  });

  test('should navigate to edit plugin page', async ({ page }) => {
    await page.goto('/en/dashboard/plugins');
    await page.waitForSelector('[data-testid="plugins-table"]', { timeout: 5000 });
    
    // 点击第一个插件的编辑按钮
    await page.click('[data-testid="plugin-row"]:first-child button:has-text("Edit")');
    
    // 验证导航到编辑插件页面
    await expect(page).toHaveURL('/en/dashboard/plugins/jwt-auth/edit');
  });

  test('should handle API errors gracefully', async ({ page }) => {
    // 设置错误响应
    await page.route('**/admin/plugins', async (route) => {
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({
          error: 'Internal Server Error',
          message: 'Failed to fetch plugins'
        })
      });
    });
    
    await page.goto('/en/dashboard/plugins');
    
    // 验证错误消息
    await expect(page.locator('.alert-title')).toHaveText('Error');
    await expect(page.locator('.alert-description')).toContainText('Failed to fetch plugins');
  });
});
