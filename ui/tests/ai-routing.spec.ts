import { test, expect } from '@playwright/test';

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
            },
            {
              id: 'rule-3',
              name: 'Authentication Header Rule',
              priority: 3,
              condition: {
                type: 'header',
                pattern: 'Authorization',
                headers: { 'Authorization': 'Bearer *' }
              },
              targetModel: 'claude-3-opus',
              enabled: false
            }
          ],
          total: 3,
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
            },
            {
              id: 'claude-3-opus',
              name: 'Claude 3 Opus',
              provider: 'Anthropic',
              enabled: true
            }
          ],
          total: 3,
          page: 1,
          pageSize: 10
        })
      });
    });
  });

  test('should list all AI routing rules', async ({ page }) => {
    await page.goto('/en/dashboard/ai-routing');
    await page.waitForSelector('[data-testid="ai-routing-table"]', { timeout: 5000 });
    
    // 验证规则列表是否加载
    const ruleRows = await page.locator('[data-testid="rule-row"]').count();
    expect(ruleRows).toBe(3);
    
    // 验证规则数据是否正确显示
    expect(await page.locator('[data-testid="rule-row"]:first-child td:nth-child(2)').textContent()).toContain('Image Processing Rule');
  });

  test('should filter rules by target model', async ({ page }) => {
    await page.goto('/en/dashboard/ai-routing');
    await page.waitForSelector('[data-testid="ai-routing-table"]', { timeout: 5000 });
    
    // 设置模拟响应
    await page.route('**/admin/ai/routing/rules?page=1&pageSize=10&sortBy=priority&sortOrder=asc&targetModel=gpt-4', async (route) => {
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
            }
          ],
          total: 1,
          page: 1,
          pageSize: 10
        })
      });
    });
    
    // 选择 GPT-4 模型
    await page.selectOption('select:has-text("Target Model")', { label: 'GPT-4' });
    
    // 验证只显示 GPT-4 模型的规则
    const ruleRows = await page.locator('[data-testid="rule-row"]').count();
    expect(ruleRows).toBe(1);
    expect(await page.locator('[data-testid="rule-row"]:first-child td:nth-child(2)').textContent()).toContain('Image Processing Rule');
  });

  test('should filter rules by status', async ({ page }) => {
    await page.goto('/en/dashboard/ai-routing');
    await page.waitForSelector('[data-testid="ai-routing-table"]', { timeout: 5000 });
    
    // 设置模拟响应
    await page.route('**/admin/ai/routing/rules?page=1&pageSize=10&sortBy=priority&sortOrder=asc&enabled=false', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          rules: [
            {
              id: 'rule-3',
              name: 'Authentication Header Rule',
              priority: 3,
              condition: {
                type: 'header',
                pattern: 'Authorization',
                headers: { 'Authorization': 'Bearer *' }
              },
              targetModel: 'claude-3-opus',
              enabled: false
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
    
    // 验证只显示禁用的规则
    const ruleRows = await page.locator('[data-testid="rule-row"]').count();
    expect(ruleRows).toBe(1);
    expect(await page.locator('[data-testid="rule-row"]:first-child td:nth-child(2)').textContent()).toContain('Authentication Header Rule');
  });

  test('should search rules', async ({ page }) => {
    await page.goto('/en/dashboard/ai-routing');
    await page.waitForSelector('[data-testid="ai-routing-table"]', { timeout: 5000 });
    
    // 设置模拟响应
    await page.route('**/admin/ai/routing/rules?page=1&pageSize=10&sortBy=priority&sortOrder=asc&search=API', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          rules: [
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
          total: 1,
          page: 1,
          pageSize: 10
        })
      });
    });
    
    // 输入搜索查询
    await page.fill('[data-testid="rule-search"]', 'API');
    
    // 验证搜索结果
    const ruleRows = await page.locator('[data-testid="rule-row"]').count();
    expect(ruleRows).toBe(1);
    expect(await page.locator('[data-testid="rule-row"]:first-child td:nth-child(2)').textContent()).toContain('API Requests Rule');
  });

  test('should toggle rule status', async ({ page }) => {
    await page.goto('/en/dashboard/ai-routing');
    await page.waitForSelector('[data-testid="ai-routing-table"]', { timeout: 5000 });
    
    // 设置模拟响应
    await page.route('**/admin/ai/routing/rules/rule-1/disable', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          rule: {
            id: 'rule-1',
            name: 'Image Processing Rule',
            priority: 1,
            condition: {
              type: 'content',
              pattern: 'image/*',
              contentTypes: ['image/jpeg', 'image/png', 'image/gif']
            },
            targetModel: 'gpt-4',
            enabled: false
          },
          message: 'Rule disabled successfully'
        })
      });
    });
    
    // 点击第一个规则的开关
    await page.click('[data-testid="rule-row"]:first-child [role="switch"]');
    
    // 验证成功消息
    await expect(page.locator('.toast-title')).toHaveText('Success');
    await expect(page.locator('.toast-description')).toContainText('has been disabled successfully');
  });

  test('should delete a rule', async ({ page }) => {
    await page.goto('/en/dashboard/ai-routing');
    await page.waitForSelector('[data-testid="ai-routing-table"]', { timeout: 5000 });
    
    // 模拟确认对话框
    page.on('dialog', dialog => dialog.accept());
    
    // 设置模拟响应
    await page.route('**/admin/ai/routing/rules/rule-1', async (route) => {
      if (route.request().method() === 'DELETE') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            success: true,
            message: 'Rule deleted successfully'
          })
        });
      }
    });
    
    // 点击第一个规则的删除按钮
    await page.click('[data-testid="rule-row"]:first-child button:has-text("Delete")');
    
    // 验证成功消息
    await expect(page.locator('.toast-title')).toHaveText('Success');
    await expect(page.locator('.toast-description')).toContainText('has been deleted successfully');
  });

  test('should change rule priority', async ({ page }) => {
    await page.goto('/en/dashboard/ai-routing');
    await page.waitForSelector('[data-testid="ai-routing-table"]', { timeout: 5000 });
    
    // 设置模拟响应
    await page.route('**/admin/ai/routing/rules/rule-1', async (route) => {
      if (route.request().method() === 'PUT') {
        const requestBody = JSON.parse(route.request().postData() || '{}');
        
        // 验证请求数据
        expect(requestBody.priority).toBe(2);
        
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            success: true,
            rule: {
              id: 'rule-1',
              name: 'Image Processing Rule',
              priority: 2,
              condition: {
                type: 'content',
                pattern: 'image/*',
                contentTypes: ['image/jpeg', 'image/png', 'image/gif']
              },
              targetModel: 'gpt-4',
              enabled: true
            },
            message: 'Rule updated successfully'
          })
        });
      }
    });
    
    // 点击第一个规则的降低优先级按钮
    await page.click('[data-testid="rule-row"]:first-child button:has-text("Move Priority Down")');
    
    // 验证成功消息
    await expect(page.locator('.toast-title')).toHaveText('Success');
    await expect(page.locator('.toast-description')).toContainText('Priority for Image Processing Rule has been changed');
  });

  test('should navigate to create rule page', async ({ page }) => {
    await page.goto('/en/dashboard/ai-routing');
    
    // 点击添加规则按钮
    await page.click('button:has-text("Add Rule")');
    
    // 验证导航到创建规则页面
    await expect(page).toHaveURL('/en/dashboard/ai-routing/create');
  });

  test('should navigate to edit rule page', async ({ page }) => {
    await page.goto('/en/dashboard/ai-routing');
    await page.waitForSelector('[data-testid="ai-routing-table"]', { timeout: 5000 });
    
    // 点击第一个规则的编辑按钮
    await page.click('[data-testid="rule-row"]:first-child button:has-text("Edit")');
    
    // 验证导航到编辑规则页面
    await expect(page).toHaveURL('/en/dashboard/ai-routing/rule-1/edit');
  });

  test('should handle API errors gracefully', async ({ page }) => {
    // 设置错误响应
    await page.route('**/admin/ai/routing/rules', async (route) => {
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({
          error: 'Internal Server Error',
          message: 'Failed to fetch AI routing rules'
        })
      });
    });
    
    await page.goto('/en/dashboard/ai-routing');
    
    // 验证错误消息
    await expect(page.locator('.alert-title')).toHaveText('Error');
    await expect(page.locator('.alert-description')).toContainText('Failed to fetch AI routing rules');
  });
});
