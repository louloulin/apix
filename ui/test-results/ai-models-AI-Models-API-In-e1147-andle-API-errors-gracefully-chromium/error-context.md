# Test info

- Name: AI Models API Integration >> should handle API errors gracefully
- Location: /Users/louloulin/Documents/linchong/actor/apix/ui/tests/ai-models.spec.ts:306:7

# Error details

```
Error: Timed out 5000ms waiting for expect(locator).toHaveText(expected)

Locator: locator('.alert-title')
Expected string: "Error"
Received: <element(s) not found>
Call log:
  - expect.toHaveText with timeout 5000ms
  - waiting for locator('.alert-title')

    at /Users/louloulin/Documents/linchong/actor/apix/ui/tests/ai-models.spec.ts:322:48
```

# Page snapshot

```yaml
- link:
  - /url: /
  - img
- button "Sign In"
- img
- paragraph: Not Found
- button "Retry"
```

# Test source

```ts
  222 |     });
  223 |     
  224 |     // 点击第一个模型的开关
  225 |     await page.click('[data-testid="model-row"]:first-child [role="switch"]');
  226 |     
  227 |     // 验证成功消息
  228 |     await expect(page.locator('.toast-title')).toHaveText('Success');
  229 |     await expect(page.locator('.toast-description')).toContainText('has been disabled successfully');
  230 |   });
  231 |
  232 |   test('should delete a model', async ({ page }) => {
  233 |     await page.goto('/en/dashboard/ai-models');
  234 |     await page.waitForSelector('[data-testid="ai-models-table"]', { timeout: 5000 });
  235 |     
  236 |     // 模拟确认对话框
  237 |     page.on('dialog', dialog => dialog.accept());
  238 |     
  239 |     // 设置模拟响应
  240 |     await page.route('**/admin/ai/models/gpt-4', async (route) => {
  241 |       if (route.request().method() === 'DELETE') {
  242 |         await route.fulfill({
  243 |           status: 200,
  244 |           contentType: 'application/json',
  245 |           body: JSON.stringify({
  246 |             success: true,
  247 |             message: 'Model deleted successfully'
  248 |           })
  249 |         });
  250 |       }
  251 |     });
  252 |     
  253 |     // 点击第一个模型的删除按钮
  254 |     await page.click('[data-testid="model-row"]:first-child button:has-text("Delete")');
  255 |     
  256 |     // 验证成功消息
  257 |     await expect(page.locator('.toast-title')).toHaveText('Success');
  258 |     await expect(page.locator('.toast-description')).toContainText('has been deleted successfully');
  259 |   });
  260 |
  261 |   test('should test model connection', async ({ page }) => {
  262 |     await page.goto('/en/dashboard/ai-models');
  263 |     await page.waitForSelector('[data-testid="ai-models-table"]', { timeout: 5000 });
  264 |     
  265 |     // 设置模拟响应
  266 |     await page.route('**/admin/ai/models/gpt-4/test', async (route) => {
  267 |       await route.fulfill({
  268 |         status: 200,
  269 |         contentType: 'application/json',
  270 |         body: JSON.stringify({
  271 |           success: true,
  272 |           message: 'Connection successful'
  273 |         })
  274 |       });
  275 |     });
  276 |     
  277 |     // 点击第一个模型的测试连接按钮
  278 |     await page.click('[data-testid="model-row"]:first-child button:has-text("Test Connection")');
  279 |     
  280 |     // 验证成功消息
  281 |     await expect(page.locator('.toast-title')).toHaveText('Success');
  282 |     await expect(page.locator('.toast-description')).toContainText('Connection to GPT-4 successful');
  283 |   });
  284 |
  285 |   test('should navigate to create model page', async ({ page }) => {
  286 |     await page.goto('/en/dashboard/ai-models');
  287 |     
  288 |     // 点击添加模型按钮
  289 |     await page.click('button:has-text("Add Model")');
  290 |     
  291 |     // 验证导航到创建模型页面
  292 |     await expect(page).toHaveURL('/en/dashboard/ai-models/create');
  293 |   });
  294 |
  295 |   test('should navigate to edit model page', async ({ page }) => {
  296 |     await page.goto('/en/dashboard/ai-models');
  297 |     await page.waitForSelector('[data-testid="ai-models-table"]', { timeout: 5000 });
  298 |     
  299 |     // 点击第一个模型的编辑按钮
  300 |     await page.click('[data-testid="model-row"]:first-child button:has-text("Edit")');
  301 |     
  302 |     // 验证导航到编辑模型页面
  303 |     await expect(page).toHaveURL('/en/dashboard/ai-models/gpt-4/edit');
  304 |   });
  305 |
  306 |   test('should handle API errors gracefully', async ({ page }) => {
  307 |     // 设置错误响应
  308 |     await page.route('**/admin/ai/models', async (route) => {
  309 |       await route.fulfill({
  310 |         status: 500,
  311 |         contentType: 'application/json',
  312 |         body: JSON.stringify({
  313 |           error: 'Internal Server Error',
  314 |           message: 'Failed to fetch AI models'
  315 |         })
  316 |       });
  317 |     });
  318 |     
  319 |     await page.goto('/en/dashboard/ai-models');
  320 |     
  321 |     // 验证错误消息
> 322 |     await expect(page.locator('.alert-title')).toHaveText('Error');
      |                                                ^ Error: Timed out 5000ms waiting for expect(locator).toHaveText(expected)
  323 |     await expect(page.locator('.alert-description')).toContainText('Failed to fetch AI models');
  324 |   });
  325 | });
  326 |
```