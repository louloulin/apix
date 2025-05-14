# Test info

- Name: AI Models API Integration >> should search models
- Location: /Users/louloulin/Documents/linchong/actor/apix/ui/tests/ai-models.spec.ts:154:7

# Error details

```
TimeoutError: page.waitForSelector: Timeout 5000ms exceeded.
Call log:
  - waiting for locator('[data-testid="ai-models-table"]') to be visible

    at /Users/louloulin/Documents/linchong/actor/apix/ui/tests/ai-models.spec.ts:156:16
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
   56 |       });
   57 |     });
   58 |   });
   59 |
   60 |   test('should list all AI models', async ({ page }) => {
   61 |     await page.goto('/en/dashboard/ai-models');
   62 |     await page.waitForSelector('[data-testid="ai-models-table"]', { timeout: 5000 });
   63 |     
   64 |     // 验证模型列表是否加载
   65 |     const modelRows = await page.locator('[data-testid="model-row"]').count();
   66 |     expect(modelRows).toBe(3);
   67 |     
   68 |     // 验证模型数据是否正确显示
   69 |     expect(await page.locator('[data-testid="model-row"]:first-child td:first-child').textContent()).toContain('GPT-4');
   70 |   });
   71 |
   72 |   test('should filter models by provider', async ({ page }) => {
   73 |     await page.goto('/en/dashboard/ai-models');
   74 |     await page.waitForSelector('[data-testid="ai-models-table"]', { timeout: 5000 });
   75 |     
   76 |     // 设置模拟响应
   77 |     await page.route('**/admin/ai/models?page=1&pageSize=10&sortBy=name&sortOrder=asc&provider=Anthropic', async (route) => {
   78 |       await route.fulfill({
   79 |         status: 200,
   80 |         contentType: 'application/json',
   81 |         body: JSON.stringify({
   82 |           models: [
   83 |             {
   84 |               id: 'claude-3-opus',
   85 |               name: 'Claude 3 Opus',
   86 |               provider: 'Anthropic',
   87 |               description: 'Anthropic Claude 3 Opus model',
   88 |               maxTokens: 100000,
   89 |               enabled: false,
   90 |               priority: 3,
   91 |               costPerToken: 0.00015,
   92 |               capabilities: ['text-generation', 'chat', 'vision'],
   93 |               contextWindow: 100000,
   94 |               version: '1.0.0'
   95 |             }
   96 |           ],
   97 |           total: 1,
   98 |           page: 1,
   99 |           pageSize: 10
  100 |         })
  101 |       });
  102 |     });
  103 |     
  104 |     // 选择 Anthropic 提供商
  105 |     await page.selectOption('select:has-text("Provider")', { label: 'Anthropic' });
  106 |     
  107 |     // 验证只显示 Anthropic 模型
  108 |     const modelRows = await page.locator('[data-testid="model-row"]').count();
  109 |     expect(modelRows).toBe(1);
  110 |     expect(await page.locator('[data-testid="model-row"]:first-child td:first-child').textContent()).toContain('Claude 3 Opus');
  111 |   });
  112 |
  113 |   test('should filter models by status', async ({ page }) => {
  114 |     await page.goto('/en/dashboard/ai-models');
  115 |     await page.waitForSelector('[data-testid="ai-models-table"]', { timeout: 5000 });
  116 |     
  117 |     // 设置模拟响应
  118 |     await page.route('**/admin/ai/models?page=1&pageSize=10&sortBy=name&sortOrder=asc&enabled=false', async (route) => {
  119 |       await route.fulfill({
  120 |         status: 200,
  121 |         contentType: 'application/json',
  122 |         body: JSON.stringify({
  123 |           models: [
  124 |             {
  125 |               id: 'claude-3-opus',
  126 |               name: 'Claude 3 Opus',
  127 |               provider: 'Anthropic',
  128 |               description: 'Anthropic Claude 3 Opus model',
  129 |               maxTokens: 100000,
  130 |               enabled: false,
  131 |               priority: 3,
  132 |               costPerToken: 0.00015,
  133 |               capabilities: ['text-generation', 'chat', 'vision'],
  134 |               contextWindow: 100000,
  135 |               version: '1.0.0'
  136 |             }
  137 |           ],
  138 |           total: 1,
  139 |           page: 1,
  140 |           pageSize: 10
  141 |         })
  142 |       });
  143 |     });
  144 |     
  145 |     // 选择 Disabled 状态
  146 |     await page.selectOption('select:has-text("Status")', { label: 'Disabled' });
  147 |     
  148 |     // 验证只显示禁用的模型
  149 |     const modelRows = await page.locator('[data-testid="model-row"]').count();
  150 |     expect(modelRows).toBe(1);
  151 |     expect(await page.locator('[data-testid="model-row"]:first-child td:first-child').textContent()).toContain('Claude 3 Opus');
  152 |   });
  153 |
  154 |   test('should search models', async ({ page }) => {
  155 |     await page.goto('/en/dashboard/ai-models');
> 156 |     await page.waitForSelector('[data-testid="ai-models-table"]', { timeout: 5000 });
      |                ^ TimeoutError: page.waitForSelector: Timeout 5000ms exceeded.
  157 |     
  158 |     // 设置模拟响应
  159 |     await page.route('**/admin/ai/models?page=1&pageSize=10&sortBy=name&sortOrder=asc&search=gpt-4', async (route) => {
  160 |       await route.fulfill({
  161 |         status: 200,
  162 |         contentType: 'application/json',
  163 |         body: JSON.stringify({
  164 |           models: [
  165 |             {
  166 |               id: 'gpt-4',
  167 |               name: 'GPT-4',
  168 |               provider: 'OpenAI',
  169 |               description: 'OpenAI GPT-4 model',
  170 |               maxTokens: 8192,
  171 |               enabled: true,
  172 |               priority: 1,
  173 |               costPerToken: 0.00006,
  174 |               capabilities: ['text-generation', 'chat', 'embeddings'],
  175 |               contextWindow: 8192,
  176 |               version: '1.0.0'
  177 |             }
  178 |           ],
  179 |           total: 1,
  180 |           page: 1,
  181 |           pageSize: 10
  182 |         })
  183 |       });
  184 |     });
  185 |     
  186 |     // 输入搜索查询
  187 |     await page.fill('[data-testid="model-search"]', 'gpt-4');
  188 |     
  189 |     // 验证搜索结果
  190 |     const modelRows = await page.locator('[data-testid="model-row"]').count();
  191 |     expect(modelRows).toBe(1);
  192 |     expect(await page.locator('[data-testid="model-row"]:first-child td:first-child').textContent()).toContain('GPT-4');
  193 |   });
  194 |
  195 |   test('should toggle model status', async ({ page }) => {
  196 |     await page.goto('/en/dashboard/ai-models');
  197 |     await page.waitForSelector('[data-testid="ai-models-table"]', { timeout: 5000 });
  198 |     
  199 |     // 设置模拟响应
  200 |     await page.route('**/admin/ai/models/gpt-4/disable', async (route) => {
  201 |       await route.fulfill({
  202 |         status: 200,
  203 |         contentType: 'application/json',
  204 |         body: JSON.stringify({
  205 |           success: true,
  206 |           model: {
  207 |             id: 'gpt-4',
  208 |             name: 'GPT-4',
  209 |             provider: 'OpenAI',
  210 |             description: 'OpenAI GPT-4 model',
  211 |             maxTokens: 8192,
  212 |             enabled: false,
  213 |             priority: 1,
  214 |             costPerToken: 0.00006,
  215 |             capabilities: ['text-generation', 'chat', 'embeddings'],
  216 |             contextWindow: 8192,
  217 |             version: '1.0.0'
  218 |           },
  219 |           message: 'Model disabled successfully'
  220 |         })
  221 |       });
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
```