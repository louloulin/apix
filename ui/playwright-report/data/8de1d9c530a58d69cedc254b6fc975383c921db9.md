# Test info

- Name: AI Models API Integration >> should filter models by provider
- Location: /Users/louloulin/Documents/linchong/actor/apix/ui/tests/ai-models.spec.ts:72:7

# Error details

```
TimeoutError: page.waitForSelector: Timeout 5000ms exceeded.
Call log:
  - waiting for locator('[data-testid="ai-models-table"]') to be visible

    at /Users/louloulin/Documents/linchong/actor/apix/ui/tests/ai-models.spec.ts:74:16
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
   1 | import { test, expect } from '@playwright/test';
   2 |
   3 | test.describe('AI Models API Integration', () => {
   4 |   test.beforeEach(async ({ page }) => {
   5 |     // 设置模拟响应
   6 |     await page.route('**/admin/ai/models', async (route) => {
   7 |       await route.fulfill({
   8 |         status: 200,
   9 |         contentType: 'application/json',
   10 |         body: JSON.stringify({
   11 |           models: [
   12 |             {
   13 |               id: 'gpt-4',
   14 |               name: 'GPT-4',
   15 |               provider: 'OpenAI',
   16 |               description: 'OpenAI GPT-4 model',
   17 |               maxTokens: 8192,
   18 |               enabled: true,
   19 |               priority: 1,
   20 |               costPerToken: 0.00006,
   21 |               capabilities: ['text-generation', 'chat', 'embeddings'],
   22 |               contextWindow: 8192,
   23 |               version: '1.0.0'
   24 |             },
   25 |             {
   26 |               id: 'gpt-3.5-turbo',
   27 |               name: 'GPT-3.5 Turbo',
   28 |               provider: 'OpenAI',
   29 |               description: 'OpenAI GPT-3.5 Turbo model',
   30 |               maxTokens: 4096,
   31 |               enabled: true,
   32 |               priority: 2,
   33 |               costPerToken: 0.00002,
   34 |               capabilities: ['text-generation', 'chat'],
   35 |               contextWindow: 4096,
   36 |               version: '1.0.0'
   37 |             },
   38 |             {
   39 |               id: 'claude-3-opus',
   40 |               name: 'Claude 3 Opus',
   41 |               provider: 'Anthropic',
   42 |               description: 'Anthropic Claude 3 Opus model',
   43 |               maxTokens: 100000,
   44 |               enabled: false,
   45 |               priority: 3,
   46 |               costPerToken: 0.00015,
   47 |               capabilities: ['text-generation', 'chat', 'vision'],
   48 |               contextWindow: 100000,
   49 |               version: '1.0.0'
   50 |             }
   51 |           ],
   52 |           total: 3,
   53 |           page: 1,
   54 |           pageSize: 10
   55 |         })
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
>  74 |     await page.waitForSelector('[data-testid="ai-models-table"]', { timeout: 5000 });
      |                ^ TimeoutError: page.waitForSelector: Timeout 5000ms exceeded.
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
  156 |     await page.waitForSelector('[data-testid="ai-models-table"]', { timeout: 5000 });
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
```