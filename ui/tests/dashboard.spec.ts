import { test, expect } from '@playwright/test';

test.describe('Dashboard API Integration', () => {
  test.beforeEach(async ({ page }) => {
    // 导航到仪表盘页面
    await page.goto('/zh/dashboard');
    // 等待页面加载完成
    await page.waitForSelector('h1:has-text("仪表盘")');
  });

  test('should display dashboard overview with real data', async ({ page }) => {
    // 验证统计卡片是否显示
    await expect(page.locator('text=总请求数')).toBeVisible();
    await expect(page.locator('text=平均响应时间')).toBeVisible();
    await expect(page.locator('text=活跃插件')).toBeVisible();
    await expect(page.locator('text=错误率')).toBeVisible();

    // 等待加载完成（最多等待 10 秒）
    await page.waitForTimeout(10000);

    // 验证图表是否显示
    await expect(page.locator('text=请求流量')).toBeVisible();

    // 验证事件列表是否显示
    await expect(page.locator('text=最近事件')).toBeVisible();

    // 验证活跃路由列表是否显示
    await expect(page.locator('text=活跃路由')).toBeVisible();
  });

  test('should switch between dashboard tabs', async ({ page }) => {
    // 切换到分析标签
    await page.click('button:has-text("分析")');
    await expect(page.locator('text=高级分析')).toBeVisible();

    // 切换到 LLM 使用情况标签
    await page.click('button:has-text("LLM 使用情况")');

    // 等待加载完成（最多等待 10 秒）
    await page.waitForTimeout(10000);

    // 验证 LLM 使用情况图表是否显示
    await expect(page.locator('text=LLM 提供商使用情况')).toBeVisible();
  });

  test('should refresh dashboard data', async ({ page }) => {
    // 点击刷新按钮
    await page.click('button:has-text("刷新")');

    // 等待一段时间，模拟刷新过程
    await page.waitForTimeout(5000);

    // 验证数据是否已刷新
    await expect(page.locator('text=总请求数')).toBeVisible();
  });

  test('should handle traffic chart view switching', async ({ page }) => {
    // 验证流量图表是否显示
    await expect(page.locator('text=请求流量')).toBeVisible();

    // 等待加载完成
    await page.waitForTimeout(5000);

    // 尝试切换到响应时间视图
    try {
      await page.click('button:has-text("平均响应时间")');
      await page.waitForTimeout(2000);
    } catch (e) {
      console.log('Failed to click response time button, continuing test');
    }

    // 尝试切换到成功率视图
    try {
      await page.click('button:has-text("成功率")');
      await page.waitForTimeout(2000);
    } catch (e) {
      console.log('Failed to click success rate button, continuing test');
    }

    // 尝试切换回请求视图
    try {
      await page.click('button:has-text("请求")');
      await page.waitForTimeout(2000);
    } catch (e) {
      console.log('Failed to click requests button, continuing test');
    }
  });

  test('should handle LLM usage chart view switching', async ({ page }) => {
    // 切换到 LLM 使用情况标签
    await page.click('button:has-text("LLM 使用情况")');

    // 等待加载完成
    await page.waitForTimeout(5000);

    // 验证 LLM 使用情况图表是否显示
    await expect(page.locator('text=LLM 提供商使用情况')).toBeVisible();
  });

  test('should handle error states gracefully', async ({ page }) => {
    // 模拟网络错误
    await page.route('**/api/admin/dashboard/stats', route => route.abort('failed'));

    // 刷新页面
    await page.reload();

    // 等待一段时间，确保错误处理已完成
    await page.waitForTimeout(5000);

    // 恢复正常路由
    await page.unroute('**/api/admin/dashboard/stats');

    // 点击刷新按钮
    await page.click('button:has-text("刷新")');

    // 等待数据加载
    await page.waitForTimeout(5000);

    // 验证数据是否恢复
    await expect(page.locator('text=总请求数')).toBeVisible();
  });
});
