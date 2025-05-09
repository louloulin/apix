import { test, expect } from '@playwright/test';

test.describe('API Keys Management', () => {
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
              },
              {
                id: 'key-3',
                key: 'apix.zyxwvutsrqponml',
                name: 'Development API Key',
                scopes: ['*'],
                enabled: true,
                createdAt: '2023-05-20T08:00:00Z',
                expiresAt: '2023-08-20T08:00:00Z',
                lastUsedAt: '2023-07-12T09:45:00Z'
              }
            ],
            total: 3,
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
            { id: 'write:routes', name: 'Write Routes', description: 'Access to modify routes' },
            { id: '*', name: 'Full Access', description: 'Full access to all API endpoints' }
          ]
        })
      });
    });
  });

  test('should list all API keys', async ({ page }) => {
    await page.goto('/en/dashboard/api-keys');
    await page.waitForSelector('[data-testid="api-keys-table"]', { timeout: 5000 });
    
    // 验证密钥列表是否加载
    const keyRows = await page.locator('[data-testid="key-row"]').count();
    expect(keyRows).toBe(3);
    
    // 验证密钥数据是否正确显示
    expect(await page.locator('[data-testid="key-row"]:first-child td:first-child').textContent()).toContain('Test API Key');
  });

  test('should filter keys by scope', async ({ page }) => {
    await page.goto('/en/dashboard/api-keys');
    await page.waitForSelector('[data-testid="api-keys-table"]', { timeout: 5000 });
    
    // 设置模拟响应
    await page.route('**/admin/auth/api-keys?page=1&pageSize=10&sortBy=createdAt&sortOrder=desc&scope=write%3Aconfig', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          keys: [
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
          total: 1,
          page: 1,
          pageSize: 10
        })
      });
    });
    
    // 选择 Write Config 权限
    await page.selectOption('select:has-text("Permission")', { label: 'Write Config' });
    
    // 验证只显示具有 write:config 权限的密钥
    const keyRows = await page.locator('[data-testid="key-row"]').count();
    expect(keyRows).toBe(1);
    expect(await page.locator('[data-testid="key-row"]:first-child td:first-child').textContent()).toContain('Production API Key');
  });

  test('should search keys', async ({ page }) => {
    await page.goto('/en/dashboard/api-keys');
    await page.waitForSelector('[data-testid="api-keys-table"]', { timeout: 5000 });
    
    // 设置模拟响应
    await page.route('**/admin/auth/api-keys?page=1&pageSize=10&sortBy=createdAt&sortOrder=desc&search=Development', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          keys: [
            {
              id: 'key-3',
              key: 'apix.zyxwvutsrqponml',
              name: 'Development API Key',
              scopes: ['*'],
              enabled: true,
              createdAt: '2023-05-20T08:00:00Z',
              expiresAt: '2023-08-20T08:00:00Z',
              lastUsedAt: '2023-07-12T09:45:00Z'
            }
          ],
          total: 1,
          page: 1,
          pageSize: 10
        })
      });
    });
    
    // 输入搜索查询
    await page.fill('[data-testid="key-search"]', 'Development');
    
    // 验证搜索结果
    const keyRows = await page.locator('[data-testid="key-row"]').count();
    expect(keyRows).toBe(1);
    expect(await page.locator('[data-testid="key-row"]:first-child td:first-child').textContent()).toContain('Development API Key');
  });

  test('should delete a key', async ({ page }) => {
    await page.goto('/en/dashboard/api-keys');
    await page.waitForSelector('[data-testid="api-keys-table"]', { timeout: 5000 });
    
    // 模拟确认对话框
    page.on('dialog', dialog => dialog.accept());
    
    // 设置模拟响应
    await page.route('**/admin/auth/api-keys/key-1', async (route) => {
      if (route.request().method() === 'DELETE') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            success: true,
            message: 'API key deleted successfully'
          })
        });
      }
    });
    
    // 点击第一个密钥的删除按钮
    await page.click('[data-testid="key-row"]:first-child button:has-text("Delete")');
    
    // 验证成功消息
    await expect(page.locator('.toast-title')).toHaveText('Success');
    await expect(page.locator('.toast-description')).toContainText('has been deleted successfully');
  });

  test('should copy a key', async ({ page }) => {
    await page.goto('/en/dashboard/api-keys');
    await page.waitForSelector('[data-testid="api-keys-table"]', { timeout: 5000 });
    
    // 模拟剪贴板
    await page.evaluate(() => {
      // @ts-ignore
      navigator.clipboard = {
        writeText: () => Promise.resolve()
      };
    });
    
    // 点击第一个密钥的复制按钮
    await page.click('[data-testid="key-row"]:first-child button:has-text("Copy API Key")');
    
    // 验证成功消息
    await expect(page.locator('.toast-title')).toHaveText('Success');
    await expect(page.locator('.toast-description')).toContainText('API key copied to clipboard');
  });

  test('should create a new API key', async ({ page }) => {
    // 设置模拟响应
    await page.route('**/admin/auth/api-keys', async (route) => {
      if (route.request().method() === 'POST') {
        const requestBody = JSON.parse(route.request().postData() || '{}');
        
        // 验证请求数据
        expect(requestBody.name).toBe('New Test Key');
        expect(requestBody.scopes).toContain('read:metrics');
        
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            success: true,
            key: {
              id: 'new-key-1',
              key: 'apix.newtestkey123456',
              name: 'New Test Key',
              scopes: requestBody.scopes,
              enabled: true,
              createdAt: new Date().toISOString(),
              description: requestBody.description
            },
            message: 'API key created successfully'
          })
        });
      }
    });
    
    await page.goto('/en/dashboard/api-keys');
    
    // 点击创建密钥按钮
    await page.click('button:has-text("Create API Key")');
    
    // 填写表单
    await page.fill('[data-testid="key-name-input"]', 'New Test Key');
    await page.check('input[id="scope-read:metrics"]');
    
    // 提交表单
    await page.click('[data-testid="create-key-button"]');
    
    // 验证成功消息
    await expect(page.locator('.toast-title')).toHaveText('Success');
    await expect(page.locator('.toast-description')).toContainText('API key has been created successfully');
    
    // 验证显示新创建的密钥
    await expect(page.locator('[data-testid="created-key-value"]')).toHaveValue('apix.newtestkey123456');
  });

  test('should copy newly created key', async ({ page }) => {
    // 设置模拟响应
    await page.route('**/admin/auth/api-keys', async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            success: true,
            key: {
              id: 'new-key-1',
              key: 'apix.newtestkey123456',
              name: 'New Test Key',
              scopes: ['read:metrics'],
              enabled: true,
              createdAt: new Date().toISOString()
            },
            message: 'API key created successfully'
          })
        });
      }
    });
    
    // 模拟剪贴板
    await page.evaluate(() => {
      // @ts-ignore
      navigator.clipboard = {
        writeText: () => Promise.resolve()
      };
    });
    
    await page.goto('/en/dashboard/api-keys');
    
    // 点击创建密钥按钮
    await page.click('button:has-text("Create API Key")');
    
    // 填写表单
    await page.fill('[data-testid="key-name-input"]', 'New Test Key');
    await page.check('input[id="scope-read:metrics"]');
    
    // 提交表单
    await page.click('[data-testid="create-key-button"]');
    
    // 点击复制按钮
    await page.click('button:has-title("Copy API Key")');
    
    // 验证成功消息
    await expect(page.locator('.toast-title')).toHaveText('Success');
    await expect(page.locator('.toast-description')).toContainText('API key copied to clipboard');
  });

  test('should handle API errors gracefully', async ({ page }) => {
    // 设置错误响应
    await page.route('**/admin/auth/api-keys', async (route) => {
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({
          error: 'Internal Server Error',
          message: 'Failed to fetch API keys'
        })
      });
    });
    
    await page.goto('/en/dashboard/api-keys');
    
    // 验证错误消息
    await expect(page.locator('.alert-title')).toHaveText('Error');
    await expect(page.locator('.alert-description')).toContainText('Failed to fetch API keys');
  });
});
