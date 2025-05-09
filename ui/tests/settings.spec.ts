import { test, expect } from '@playwright/test';

test.describe('Settings API Integration', () => {
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
                },
                cors: {
                  enabled: true,
                  allowedOrigins: ['*'],
                  allowedMethods: ['GET', 'POST', 'PUT', 'DELETE']
                }
              },
              admin: {
                enabled: true,
                host: '0.0.0.0',
                port: 9090,
                auth: {
                  enabled: false,
                  type: 'basic'
                }
              },
              logging: {
                level: 'INFO',
                file: '/var/log/apix.log',
                console: true
              },
              metrics: {
                enabled: false,
                prometheus: {
                  enabled: false,
                  path: '/metrics'
                }
              },
              cluster: {
                enabled: false,
                nodes: [],
                nodeName: ''
              }
            }
          })
        });
      }
    });
  });

  test('should load configuration', async ({ page }) => {
    await page.goto('/en/dashboard/settings');
    
    // 等待配置加载完成
    await page.waitForSelector('[data-testid="gateway-host"]', { timeout: 5000 });
    
    // 验证配置是否正确加载
    expect(await page.inputValue('[data-testid="gateway-host"]')).toBe('0.0.0.0');
    expect(await page.inputValue('[data-testid="gateway-port"]')).toBe('8080');
    expect(await page.isChecked('[data-testid="ssl-enabled"]')).toBe(false);
  });

  test('should update configuration', async ({ page }) => {
    // 设置模拟响应
    await page.route('**/admin/config', async (route) => {
      if (route.request().method() === 'PUT') {
        const requestBody = JSON.parse(route.request().postData() || '{}');
        
        // 验证请求数据
        expect(requestBody.gateway.host).toBe('127.0.0.1');
        expect(requestBody.gateway.port).toBe(8081);
        
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            success: true,
            message: 'Configuration updated successfully',
            config: {
              ...requestBody
            }
          })
        });
      }
    });
    
    await page.goto('/en/dashboard/settings');
    await page.waitForSelector('[data-testid="gateway-host"]', { timeout: 5000 });
    
    // 修改配置
    await page.fill('[data-testid="gateway-host"]', '127.0.0.1');
    await page.fill('[data-testid="gateway-port"]', '8081');
    
    // 保存配置
    await page.click('button:has-text("Save")');
    
    // 验证成功消息
    await expect(page.locator('.toast-title')).toHaveText('Success');
    await expect(page.locator('.toast-description')).toContainText('Configuration saved successfully');
  });

  test('should handle JSON editing', async ({ page }) => {
    await page.goto('/en/dashboard/settings');
    await page.waitForSelector('[data-testid="gateway-host"]', { timeout: 5000 });
    
    // 切换到 JSON 标签
    await page.click('button:has-text("JSON")');
    
    // 等待 JSON 编辑器加载
    await page.waitForSelector('[data-testid="config-json"]');
    
    // 修改 JSON
    const newConfig = {
      gateway: {
        host: '127.0.0.1',
        port: 8081,
        ssl: {
          enabled: true,
          certPath: '/path/to/cert.pem',
          keyPath: '/path/to/key.pem'
        }
      },
      admin: {
        enabled: true,
        host: '127.0.0.1',
        port: 9091
      },
      logging: {
        level: 'DEBUG',
        console: true
      }
    };
    
    await page.fill('[data-testid="config-json"]', JSON.stringify(newConfig, null, 2));
    
    // 设置模拟响应
    await page.route('**/admin/config', async (route) => {
      if (route.request().method() === 'PUT') {
        const requestBody = JSON.parse(route.request().postData() || '{}');
        
        // 验证请求数据
        expect(requestBody.gateway.host).toBe('127.0.0.1');
        expect(requestBody.gateway.port).toBe(8081);
        expect(requestBody.gateway.ssl.enabled).toBe(true);
        
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            success: true,
            message: 'Configuration updated successfully',
            config: {
              ...requestBody
            }
          })
        });
      }
    });
    
    // 保存配置
    await page.click('button:has-text("Save")');
    
    // 验证成功消息
    await expect(page.locator('.toast-title')).toHaveText('Success');
    await expect(page.locator('.toast-description')).toContainText('Configuration saved successfully');
  });

  test('should handle invalid JSON', async ({ page }) => {
    await page.goto('/en/dashboard/settings');
    await page.waitForSelector('[data-testid="gateway-host"]', { timeout: 5000 });
    
    // 切换到 JSON 标签
    await page.click('button:has-text("JSON")');
    
    // 等待 JSON 编辑器加载
    await page.waitForSelector('[data-testid="config-json"]');
    
    // 输入无效的 JSON
    await page.fill('[data-testid="config-json"]', '{ "gateway": { "host": "127.0.0.1", "port": 8081, }');
    
    // 保存配置
    await page.click('button:has-text("Save")');
    
    // 验证错误消息
    await expect(page.locator('.alert-title')).toHaveText('Error');
    await expect(page.locator('.alert-description')).toContainText('Invalid JSON format');
  });

  test('should handle reload config', async ({ page }) => {
    // 设置模拟响应
    await page.route('**/admin/config/reload', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          message: 'Configuration reloaded successfully'
        })
      });
    });
    
    await page.goto('/en/dashboard/settings');
    await page.waitForSelector('[data-testid="gateway-host"]', { timeout: 5000 });
    
    // 点击重新加载配置按钮
    await page.click('button:has-text("Reload Config")');
    
    // 验证成功消息
    await expect(page.locator('.toast-title')).toHaveText('Success');
    await expect(page.locator('.toast-description')).toContainText('Configuration reloaded successfully');
  });

  test('should handle restart gateway', async ({ page }) => {
    // 设置模拟响应
    await page.route('**/admin/restart', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          message: 'Gateway restart initiated successfully'
        })
      });
    });
    
    // 模拟确认对话框
    page.on('dialog', dialog => dialog.accept());
    
    await page.goto('/en/dashboard/settings');
    await page.waitForSelector('[data-testid="gateway-host"]', { timeout: 5000 });
    
    // 点击重启网关按钮
    await page.click('button:has-text("Restart Gateway")');
    
    // 验证成功消息
    await expect(page.locator('.toast-title')).toHaveText('Success');
    await expect(page.locator('.toast-description')).toContainText('Gateway restart initiated successfully');
  });

  test('should handle API errors gracefully', async ({ page }) => {
    // 设置错误响应
    await page.route('**/admin/config', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 500,
          contentType: 'application/json',
          body: JSON.stringify({
            error: 'Internal Server Error',
            message: 'Failed to fetch configuration'
          })
        });
      }
    });
    
    await page.goto('/en/dashboard/settings');
    
    // 验证错误消息
    await expect(page.locator('.toast-title')).toHaveText('Error');
    await expect(page.locator('.toast-description')).toContainText('Failed to fetch configuration');
  });
});
