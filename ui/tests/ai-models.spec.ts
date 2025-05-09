import { test, expect } from '@playwright/test';

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
              capabilities: ['text-generation', 'chat', 'embeddings'],
              contextWindow: 8192,
              version: '1.0.0'
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
              capabilities: ['text-generation', 'chat'],
              contextWindow: 4096,
              version: '1.0.0'
            },
            {
              id: 'claude-3-opus',
              name: 'Claude 3 Opus',
              provider: 'Anthropic',
              description: 'Anthropic Claude 3 Opus model',
              maxTokens: 100000,
              enabled: false,
              priority: 3,
              costPerToken: 0.00015,
              capabilities: ['text-generation', 'chat', 'vision'],
              contextWindow: 100000,
              version: '1.0.0'
            }
          ],
          total: 3,
          page: 1,
          pageSize: 10
        })
      });
    });
  });

  test('should list all AI models', async ({ page }) => {
    await page.goto('/en/dashboard/ai-models');
    await page.waitForSelector('[data-testid="ai-models-table"]', { timeout: 5000 });
    
    // 验证模型列表是否加载
    const modelRows = await page.locator('[data-testid="model-row"]').count();
    expect(modelRows).toBe(3);
    
    // 验证模型数据是否正确显示
    expect(await page.locator('[data-testid="model-row"]:first-child td:first-child').textContent()).toContain('GPT-4');
  });

  test('should filter models by provider', async ({ page }) => {
    await page.goto('/en/dashboard/ai-models');
    await page.waitForSelector('[data-testid="ai-models-table"]', { timeout: 5000 });
    
    // 设置模拟响应
    await page.route('**/admin/ai/models?page=1&pageSize=10&sortBy=name&sortOrder=asc&provider=Anthropic', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          models: [
            {
              id: 'claude-3-opus',
              name: 'Claude 3 Opus',
              provider: 'Anthropic',
              description: 'Anthropic Claude 3 Opus model',
              maxTokens: 100000,
              enabled: false,
              priority: 3,
              costPerToken: 0.00015,
              capabilities: ['text-generation', 'chat', 'vision'],
              contextWindow: 100000,
              version: '1.0.0'
            }
          ],
          total: 1,
          page: 1,
          pageSize: 10
        })
      });
    });
    
    // 选择 Anthropic 提供商
    await page.selectOption('select:has-text("Provider")', { label: 'Anthropic' });
    
    // 验证只显示 Anthropic 模型
    const modelRows = await page.locator('[data-testid="model-row"]').count();
    expect(modelRows).toBe(1);
    expect(await page.locator('[data-testid="model-row"]:first-child td:first-child').textContent()).toContain('Claude 3 Opus');
  });

  test('should filter models by status', async ({ page }) => {
    await page.goto('/en/dashboard/ai-models');
    await page.waitForSelector('[data-testid="ai-models-table"]', { timeout: 5000 });
    
    // 设置模拟响应
    await page.route('**/admin/ai/models?page=1&pageSize=10&sortBy=name&sortOrder=asc&enabled=false', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          models: [
            {
              id: 'claude-3-opus',
              name: 'Claude 3 Opus',
              provider: 'Anthropic',
              description: 'Anthropic Claude 3 Opus model',
              maxTokens: 100000,
              enabled: false,
              priority: 3,
              costPerToken: 0.00015,
              capabilities: ['text-generation', 'chat', 'vision'],
              contextWindow: 100000,
              version: '1.0.0'
            }
          ],
          total: 1,
          page: 1,
          pageSize: 10
        })
      });
    });
    
    // 选择 Disabled 状态
    await page.selectOption('select:has-text("Status")', { label: 'Disabled' });
    
    // 验证只显示禁用的模型
    const modelRows = await page.locator('[data-testid="model-row"]').count();
    expect(modelRows).toBe(1);
    expect(await page.locator('[data-testid="model-row"]:first-child td:first-child').textContent()).toContain('Claude 3 Opus');
  });

  test('should search models', async ({ page }) => {
    await page.goto('/en/dashboard/ai-models');
    await page.waitForSelector('[data-testid="ai-models-table"]', { timeout: 5000 });
    
    // 设置模拟响应
    await page.route('**/admin/ai/models?page=1&pageSize=10&sortBy=name&sortOrder=asc&search=gpt-4', async (route) => {
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
              capabilities: ['text-generation', 'chat', 'embeddings'],
              contextWindow: 8192,
              version: '1.0.0'
            }
          ],
          total: 1,
          page: 1,
          pageSize: 10
        })
      });
    });
    
    // 输入搜索查询
    await page.fill('[data-testid="model-search"]', 'gpt-4');
    
    // 验证搜索结果
    const modelRows = await page.locator('[data-testid="model-row"]').count();
    expect(modelRows).toBe(1);
    expect(await page.locator('[data-testid="model-row"]:first-child td:first-child').textContent()).toContain('GPT-4');
  });

  test('should toggle model status', async ({ page }) => {
    await page.goto('/en/dashboard/ai-models');
    await page.waitForSelector('[data-testid="ai-models-table"]', { timeout: 5000 });
    
    // 设置模拟响应
    await page.route('**/admin/ai/models/gpt-4/disable', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          model: {
            id: 'gpt-4',
            name: 'GPT-4',
            provider: 'OpenAI',
            description: 'OpenAI GPT-4 model',
            maxTokens: 8192,
            enabled: false,
            priority: 1,
            costPerToken: 0.00006,
            capabilities: ['text-generation', 'chat', 'embeddings'],
            contextWindow: 8192,
            version: '1.0.0'
          },
          message: 'Model disabled successfully'
        })
      });
    });
    
    // 点击第一个模型的开关
    await page.click('[data-testid="model-row"]:first-child [role="switch"]');
    
    // 验证成功消息
    await expect(page.locator('.toast-title')).toHaveText('Success');
    await expect(page.locator('.toast-description')).toContainText('has been disabled successfully');
  });

  test('should delete a model', async ({ page }) => {
    await page.goto('/en/dashboard/ai-models');
    await page.waitForSelector('[data-testid="ai-models-table"]', { timeout: 5000 });
    
    // 模拟确认对话框
    page.on('dialog', dialog => dialog.accept());
    
    // 设置模拟响应
    await page.route('**/admin/ai/models/gpt-4', async (route) => {
      if (route.request().method() === 'DELETE') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            success: true,
            message: 'Model deleted successfully'
          })
        });
      }
    });
    
    // 点击第一个模型的删除按钮
    await page.click('[data-testid="model-row"]:first-child button:has-text("Delete")');
    
    // 验证成功消息
    await expect(page.locator('.toast-title')).toHaveText('Success');
    await expect(page.locator('.toast-description')).toContainText('has been deleted successfully');
  });

  test('should test model connection', async ({ page }) => {
    await page.goto('/en/dashboard/ai-models');
    await page.waitForSelector('[data-testid="ai-models-table"]', { timeout: 5000 });
    
    // 设置模拟响应
    await page.route('**/admin/ai/models/gpt-4/test', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          message: 'Connection successful'
        })
      });
    });
    
    // 点击第一个模型的测试连接按钮
    await page.click('[data-testid="model-row"]:first-child button:has-text("Test Connection")');
    
    // 验证成功消息
    await expect(page.locator('.toast-title')).toHaveText('Success');
    await expect(page.locator('.toast-description')).toContainText('Connection to GPT-4 successful');
  });

  test('should navigate to create model page', async ({ page }) => {
    await page.goto('/en/dashboard/ai-models');
    
    // 点击添加模型按钮
    await page.click('button:has-text("Add Model")');
    
    // 验证导航到创建模型页面
    await expect(page).toHaveURL('/en/dashboard/ai-models/create');
  });

  test('should navigate to edit model page', async ({ page }) => {
    await page.goto('/en/dashboard/ai-models');
    await page.waitForSelector('[data-testid="ai-models-table"]', { timeout: 5000 });
    
    // 点击第一个模型的编辑按钮
    await page.click('[data-testid="model-row"]:first-child button:has-text("Edit")');
    
    // 验证导航到编辑模型页面
    await expect(page).toHaveURL('/en/dashboard/ai-models/gpt-4/edit');
  });

  test('should handle API errors gracefully', async ({ page }) => {
    // 设置错误响应
    await page.route('**/admin/ai/models', async (route) => {
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({
          error: 'Internal Server Error',
          message: 'Failed to fetch AI models'
        })
      });
    });
    
    await page.goto('/en/dashboard/ai-models');
    
    // 验证错误消息
    await expect(page.locator('.alert-title')).toHaveText('Error');
    await expect(page.locator('.alert-description')).toContainText('Failed to fetch AI models');
  });
});
