# APIX UI 与后端 API 对接规划

## 概述

本文档详细说明了 APIX UI 与 APIX 后端 API 的对接规划，包括所有需要实现的 API 端点、参数和响应格式。目前 UI 中使用了大量的模拟数据，需要将其替换为真实的后端 API 调用。

## 实现状态

- ✅ 已完成
- 🔄 进行中
- ⏳ 待实现

| 功能模块 | 状态 | 测试状态 | 完成日期 |
|---------|------|---------|--------|
| 基础 API 客户端 | ✅ | ✅ | 2023-07-10 |
| 路由管理 API | ✅ | ✅ | 2023-07-10 |
| 插件管理 API | ✅ | ✅ | 2023-07-11 |
| 服务管理 API | ✅ | ✅ | 2023-07-12 |
| 配置管理 API | ✅ | ✅ | 2023-07-13 |
| 系统指标 API | ⏳ | ⏳ | - |
| AI 模型管理 API | ⏳ | ⏳ | - |
| AI 路由规则 API | ⏳ | ⏳ | - |
| API 密钥管理 API | ⏳ | ⏳ | - |

## 基础 API 客户端

**状态：✅ 已实现**

APIX UI 使用了一个增强的基础 API 客户端类 `ApiClient`，它提供了基本的 HTTP 方法（GET、POST、PUT、DELETE、PATCH）来与后端 API 交互。所有特定功能的 API 客户端都继承自这个基类。

```typescript
// 基础 API 客户端路径: ui/lib/api-client/base.ts
const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080';

// 请求超时时间（毫秒）
const DEFAULT_TIMEOUT = 30000;

// 重试次数
const DEFAULT_RETRY_COUNT = 3;

// 重试延迟（毫秒）
const DEFAULT_RETRY_DELAY = 1000;

/**
 * API 错误类
 */
export class ApiError extends Error {
  status: number;
  data?: any;

  constructor(message: string, status: number, data?: any) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.data = data;
  }
}

export class ApiClient {
  private baseUrl: string;
  private timeout: number;
  private retryCount: number;
  private retryDelay: number;

  constructor(
    baseUrl: string = API_BASE_URL,
    timeout: number = DEFAULT_TIMEOUT,
    retryCount: number = DEFAULT_RETRY_COUNT,
    retryDelay: number = DEFAULT_RETRY_DELAY
  ) {
    this.baseUrl = baseUrl;
    this.timeout = timeout;
    this.retryCount = retryCount;
    this.retryDelay = retryDelay;
  }

  // 获取认证头
  private getAuthHeaders(): Record<string, string> { ... }

  // 构建 URL
  private buildUrl(path: string): string { ... }

  // 处理响应
  private async handleResponse<T>(response: Response): Promise<T> { ... }

  // 支持重试的请求
  private async fetchWithRetry<T>(url: string, options: RequestInit): Promise<T> { ... }

  // HTTP 方法
  async get<T>(path: string, params?: Record<string, string | number | boolean>): Promise<T> { ... }
  async post<T>(path: string, data?: any): Promise<T> { ... }
  async put<T>(path: string, data: any): Promise<T> { ... }
  async delete<T>(path: string): Promise<T> { ... }
  async patch<T>(path: string, data: any): Promise<T> { ... }
}
```

### 实现说明

基础 API 客户端已经实现，包括以下增强功能：

1. **错误处理**：添加了专用的 `ApiError` 类，包含状态码和错误数据

2. **重试机制**：支持在请求失败时自动重试，可配置重试次数和延迟

3. **超时控制**：使用 `AbortController` 实现请求超时控制

4. **认证支持**：自动从 localStorage 获取认证令牌并添加到请求头

5. **查询参数支持**：增强了 GET 请求的查询参数支持

6. **内容类型处理**：根据响应的 Content-Type 自动处理不同类型的响应数据
```

## API 端点对接规划

### 1. 路由管理 API

**状态：✅ 已实现**

#### 后端 API 端点

```
GET    /admin/routes          - 获取所有路由
POST   /admin/routes          - 创建新路由
GET    /admin/routes/:id      - 获取特定路由
PUT    /admin/routes/:id      - 更新特定路由
DELETE /admin/routes/:id      - 删除特定路由
POST   /admin/routes/:id/enable  - 启用路由
POST   /admin/routes/:id/disable - 禁用路由
```

#### 数据模型

```typescript
interface Route {
  id: string;
  name?: string;
  path: string;
  targetUrl: string;
  methods: string[];
  plugins: string[];
  enabled: boolean;
  priority: number;
  type?: string;
  description?: string;
  createdAt?: string;
  updatedAt?: string;
}

interface RoutesResponse {
  routes: Route[];
  total: number;
  page: number;
  pageSize: number;
}

interface RouteResponse {
  route: Route;
}

interface RouteActionResponse {
  success: boolean;
  route: Route;
  message?: string;
}

interface RouteDeleteResponse {
  success: boolean;
  message?: string;
}
```

#### 实现计划

1. 更新 `ui/lib/api-client/routes.ts` 中的 `Route` 接口，确保与后端模型一致 (✅ 已完成)
2. 确保 `RoutesApiClient` 类中的方法正确调用后端 API (✅ 已完成)
3. 更新路由列表页面，使用新的 API 客户端 (✅ 已完成)

#### 实现说明

路由管理 API 已经实现，包括以下功能：

1. 基础 API 客户端增强：
   - 添加了错误处理和重试机制
   - 添加了超时控制
   - 添加了认证头支持

2. 路由 API 客户端增强：
   - 实现了完整的路由 CRUD 操作
   - 添加了启用/禁用路由的支持
   - 添加了查询参数支持（分页、搜索、过滤、排序）

3. 路由列表页面更新：
   - 使用真实 API 替换模拟数据
   - 添加了加载状态和错误处理
   - 添加了刷新功能
   - 改进了过滤和搜索功能

4. 测试验证：
   - 编写了完整的 Playwright 测试用例
   - 测试覆盖了所有主要功能和错误处理

### 2. 插件管理 API

**状态：✅ 已实现**

#### 后端 API 端点

```
GET    /admin/plugins          - 获取所有插件
GET    /admin/plugins/:id      - 获取特定插件
POST   /admin/plugins          - 创建新插件
PUT    /admin/plugins/:id      - 更新特定插件
DELETE /admin/plugins/:id      - 删除特定插件
POST   /admin/plugins/:id/enable  - 启用插件
POST   /admin/plugins/:id/disable - 禁用插件
POST   /admin/plugins/:id/reload  - 重新加载插件
GET    /admin/plugins/types    - 获取可用的插件类型
```

#### 数据模型

```typescript
interface Plugin {
  id: string;
  type: string;
  config: Record<string, any>;
  status: 'enabled' | 'disabled' | 'error';
  version?: string;
  name?: string;
  description?: string;
  author?: string;
  repository?: string;
  dependencies?: string[];
  createdAt?: string;
  updatedAt?: string;
}

interface PluginType {
  id: string;
  name: string;
  description: string;
  configSchema?: Record<string, any>;
  defaultConfig?: Record<string, any>;
  version?: string;
  author?: string;
  repository?: string;
}

interface PluginsResponse {
  plugins: Plugin[];
  total: number;
  page: number;
  pageSize: number;
}

interface PluginResponse {
  plugin: Plugin;
}

interface PluginTypesResponse {
  types: PluginType[];
}

interface PluginActionResponse {
  success: boolean;
  plugin?: Plugin;
  message?: string;
}

interface PluginDeleteResponse {
  success: boolean;
  message?: string;
}

interface PluginQueryParams {
  page?: number;
  pageSize?: number;
  search?: string;
  type?: string;
  status?: PluginStatus;
  sortBy?: string;
  sortOrder?: 'asc' | 'desc';
}
```

#### 实现计划

1. 更新 `ui/lib/api-client/plugins.ts` 中的接口定义，确保与后端模型一致 (✅ 已完成)
2. 确保 `PluginApiClient` 类中的方法正确调用后端 API (✅ 已完成)
3. 更新插件列表页面，使用新的 API 客户端 (✅ 已完成)

#### 实现说明

插件管理 API 已经实现，包括以下功能：

1. 插件 API 客户端增强：
   - 实现了完整的插件 CRUD 操作
   - 添加了启用/禁用/重新加载插件的支持
   - 添加了查询参数支持（分页、搜索、过滤、排序）

2. 插件列表页面更新：
   - 使用真实 API 替换模拟数据
   - 添加了加载状态和错误处理
   - 添加了刷新功能
   - 改进了过滤和搜索功能
   - 添加了状态过滤功能

3. 测试验证：
   - 编写了完整的 Playwright 测试用例
   - 测试覆盖了所有主要功能和错误处理
   - 测试包括列表显示、过滤、搜索、状态切换、删除和导航等功能

### 3. 服务管理 API

**状态：✅ 已实现**

#### 后端 API 端点

```
GET    /admin/services          - 获取所有服务
POST   /admin/services          - 创建新服务
GET    /admin/services/:id      - 获取特定服务
PUT    /admin/services/:id      - 更新特定服务
DELETE /admin/services/:id      - 删除特定服务
GET    /admin/services/:id/health - 获取服务健康状态
POST   /admin/services/:id/enable  - 启用服务
POST   /admin/services/:id/disable - 禁用服务
```

#### 数据模型

```typescript
interface Service {
  id: string;
  name: string;
  url: string;
  protocol: string;
  host: string;
  port: number;
  path: string;
  retries: number;
  connectTimeout: number;
  readTimeout: number;
  enabled: boolean;
  description?: string;
  createdAt?: string;
  updatedAt?: string;
}

interface ServiceHealth {
  status: 'UP' | 'DOWN' | 'UNKNOWN';
  timestamp: number;
  details?: Record<string, any>;
}

interface ServicesResponse {
  services: Service[];
  total: number;
  page: number;
  pageSize: number;
}

interface ServiceResponse {
  service: Service;
}

interface ServiceActionResponse {
  success: boolean;
  service: Service;
  message?: string;
}

interface ServiceDeleteResponse {
  success: boolean;
  message?: string;
}

interface ServiceQueryParams {
  page?: number;
  pageSize?: number;
  search?: string;
  enabled?: boolean;
  sortBy?: string;
  sortOrder?: 'asc' | 'desc';
}
```

#### 实现计划

1. 更新 `ui/lib/api-client/services.ts` 中的接口定义，确保与后端模型一致 (✅ 已完成)
2. 确保 `ServicesApiClient` 类中的方法正确调用后端 API (✅ 已完成)
3. 创建服务列表页面，使用新的 API 客户端 (✅ 已完成)

#### 实现说明

服务管理 API 已经实现，包括以下功能：

1. 服务 API 客户端增强：
   - 实现了完整的服务 CRUD 操作
   - 添加了启用/禁用服务的支持
   - 添加了服务健康检查功能
   - 添加了查询参数支持（分页、搜索、过滤、排序）

2. 服务列表页面实现：
   - 创建了完整的服务列表页面
   - 添加了加载状态和错误处理
   - 添加了刷新功能
   - 实现了过滤和搜索功能
   - 实现了服务健康检查功能

3. 测试验证：
   - 编写了完整的 Playwright 测试用例
   - 测试覆盖了所有主要功能和错误处理
   - 测试包括列表显示、过滤、搜索、状态切换、删除、健康检查和导航等功能

### 4. 配置管理 API

**状态：✅ 已实现**

#### 后端 API 端点

```
GET    /admin/config          - 获取系统配置
PUT    /admin/config          - 更新系统配置
POST   /admin/config/reload   - 重新加载配置
POST   /admin/restart         - 重启网关
```

#### 数据模型

```typescript
interface GatewayConfig {
  gateway: {
    host: string;
    port: number;
    ssl?: {
      enabled: boolean;
      certPath?: string;
      keyPath?: string;
    };
    cors?: {
      enabled: boolean;
      allowedOrigins?: string[];
      allowedMethods?: string[];
      allowedHeaders?: string[];
      exposedHeaders?: string[];
      allowCredentials?: boolean;
      maxAge?: number;
    };
    compression?: {
      enabled: boolean;
      level?: number;
      minSize?: number;
    };
    requestTimeout?: number;
    idleTimeout?: number;
    maxHeaderSize?: number;
    maxBodySize?: number;
  };
  admin: {
    enabled: boolean;
    host: string;
    port: number;
    ssl?: {
      enabled: boolean;
      certPath?: string;
      keyPath?: string;
    };
    auth?: {
      enabled: boolean;
      type: string;
      users?: Array<{
        username: string;
        password: string;
        roles: string[];
      }>;
    };
  };
  logging: {
    level: string;
    file?: string;
    console: boolean;
    format?: string;
  };
  metrics?: {
    enabled: boolean;
    prometheus?: {
      enabled: boolean;
      path?: string;
    };
  };
  cluster?: {
    enabled: boolean;
    nodes?: string[];
    nodeName?: string;
  };
  plugins?: Array<{
    id: string;
    type: string;
    config: Record<string, any>;
    enabled: boolean;
  }>;
  routes?: Array<Route>;
  services?: Array<Service>;
}

interface ConfigResponse {
  config: GatewayConfig;
}

interface ConfigUpdateResponse {
  success: boolean;
  message?: string;
  config?: GatewayConfig;
}
```

#### 实现计划

1. 更新 `ui/lib/api-client/config.ts` 中的接口定义，确保与后端模型一致 (✅ 已完成)
2. 确保 `ConfigApiClient` 类中的方法正确调用后端 API (✅ 已完成)
3. 创建配置页面，使用新的 API 客户端 (✅ 已完成)

#### 实现说明

配置管理 API 已经实现，包括以下功能：

1. 配置 API 客户端增强：
   - 实现了完整的配置获取和更新功能
   - 添加了重新加载配置和重启网关的支持
   - 定义了详细的配置数据模型

2. 配置页面实现：
   - 创建了完整的配置页面，支持分标签管理不同类型的配置
   - 支持通过表单和 JSON 编辑器两种方式编辑配置
   - 添加了加载状态和错误处理
   - 实现了重新加载配置和重启网关功能
   - 添加了 JSON 格式验证

3. 测试验证：
   - 编写了完整的 Playwright 测试用例
   - 测试覆盖了所有主要功能和错误处理
   - 测试包括配置加载、更新、JSON 编辑、重新加载和重启网关等功能

### 5. 系统指标 API

#### 后端 API 端点

```
GET    /admin/metrics          - 获取所有系统指标
GET    /admin/metrics/cpu      - 获取 CPU 指标
GET    /admin/metrics/memory   - 获取内存指标
GET    /admin/metrics/threads  - 获取线程指标
GET    /admin/metrics/jvm      - 获取 JVM 指标
GET    /admin/metrics/os       - 获取操作系统指标
GET    /admin/health           - 获取系统健康状态
```

#### 数据模型

```typescript
interface Metrics {
  timestamp: number;
  cpu: {
    cores: number;
    systemLoad: number;
    processCpuLoad: number;
    processCpuTime: number;
  };
  memory: {
    heap: {
      init: number;
      used: number;
      committed: number;
      max: number;
    };
    nonHeap: {
      init: number;
      used: number;
      committed: number;
      max: number;
    };
  };
  threads: {
    count: number;
    peakCount: number;
    daemonCount: number;
    totalStarted: number;
    threadDetails: Record<string, number>;
  };
  jvm: {
    name: string;
    vendor: string;
    version: string;
    uptime: number;
    startTime: number;
    systemProperties: Record<string, string>;
  };
  os: {
    name: string;
    version: string;
    arch: string;
    availableProcessors: number;
  };
}

interface Health {
  status: 'UP' | 'DOWN';
  timestamp: number;
  checks: Array<{
    name: string;
    status: 'UP' | 'DOWN';
    details?: Record<string, any>;
  }>;
}
```

#### 实现计划

1. 更新 `ui/lib/api-client/metrics.ts` 中的方法，确保正确调用后端 API
2. 移除 `ui/app/api/metrics/route.ts` 和 `ui/app/api/health/route.ts` 中的模拟数据

### 6. AI 模型管理 API

#### 后端 API 端点

```
GET    /admin/ai/models          - 获取所有 AI 模型
POST   /admin/ai/models          - 创建新 AI 模型
GET    /admin/ai/models/:id      - 获取特定 AI 模型
PUT    /admin/ai/models/:id      - 更新特定 AI 模型
DELETE /admin/ai/models/:id      - 删除特定 AI 模型
POST   /admin/ai/models/:id/enable  - 启用 AI 模型
POST   /admin/ai/models/:id/disable - 禁用 AI 模型
```

#### 数据模型

```typescript
interface AIModel {
  id: string;
  name: string;
  provider: string;
  type: string;
  version: string;
  contextWindow: number;
  maxTokens: number;
  enabled: boolean;
  config: Record<string, any>;
}
```

#### 实现计划

1. 更新 `ui/lib/api-client/ai-models.ts` 中的接口定义，确保与后端模型一致
2. 确保 `AIModelsApiClient` 类中的方法正确调用后端 API
3. 移除 `ui/app/api/ai/models` 相关文件中的模拟数据

### 7. AI 路由规则 API

#### 后端 API 端点

```
GET    /admin/ai/routing/rules          - 获取所有 AI 路由规则
POST   /admin/ai/routing/rules          - 创建新 AI 路由规则
GET    /admin/ai/routing/rules/:id      - 获取特定 AI 路由规则
PUT    /admin/ai/routing/rules/:id      - 更新特定 AI 路由规则
DELETE /admin/ai/routing/rules/:id      - 删除特定 AI 路由规则
POST   /admin/ai/routing/rules/:id/enable  - 启用 AI 路由规则
POST   /admin/ai/routing/rules/:id/disable - 禁用 AI 路由规则
```

#### 数据模型

```typescript
interface AIRoutingRule {
  id: string;
  name: string;
  priority: number;
  condition: {
    type: string;
    pattern: string;
    contentTypes?: string[];
    requestTypes?: string[];
    // 其他条件...
  };
  targetModel: string;
  enabled: boolean;
}
```

#### 实现计划

1. 创建 `ui/lib/api-client/ai-routing.ts` 文件，实现 `AIRoutingApiClient` 类
2. 在 `ui/lib/api-client.ts` 中导出新的 API 客户端
3. 移除 `ui/app/api/ai/routing` 相关文件中的模拟数据

### 8. API 密钥管理 API

#### 后端 API 端点

```
GET    /admin/auth/api-keys          - 获取所有 API 密钥
POST   /admin/auth/api-keys          - 创建新 API 密钥
DELETE /admin/auth/api-keys/:id      - 删除特定 API 密钥
```

#### 数据模型

```typescript
interface ApiKey {
  id: string;
  key: string;
  name: string;
  scopes: string[];
  enabled: boolean;
  createdAt: number;
  expiresAt: number;
}
```

#### 实现计划

1. 更新 `ui/lib/api-client/auth.ts` 中的接口定义，确保与后端模型一致
2. 确保 `AuthApiClient` 类中的方法正确调用后端 API
3. 移除 `ui/app/api/auth` 相关文件中的模拟数据

## 实现步骤

### 第一阶段：API 客户端更新

1. 更新所有 API 客户端接口定义，确保与后端模型一致
2. 确保所有 API 客户端方法正确调用后端 API 端点
3. 添加错误处理和重试逻辑

### 第二阶段：移除模拟数据

1. 移除所有 `ui/app/api` 目录下的模拟数据
2. 将请求直接转发到后端 API
3. 保留错误处理逻辑，确保在后端不可用时提供友好的错误信息

### 第三阶段：UI 组件更新

1. 更新 UI 组件，确保它们能够正确处理后端 API 返回的数据格式
2. 添加加载状态和错误处理
3. 确保所有表单提交正确调用后端 API

### 第四阶段：测试和优化

1. 测试所有 API 调用，确保它们能够正确工作
2. 优化 API 调用，减少不必要的请求
3. 添加缓存机制，提高性能

## 测试验证

每个功能模块实现后，需要进行全面的测试验证，确保其正确性和稳定性。测试将使用 Playwright 进行自动化端到端测试。

### 测试范围

每个功能模块的测试应该包括以下方面：

1. **基本功能测试**：验证所有 CRUD 操作是否正常工作
2. **错误处理测试**：验证在各种错误情况下的行为
3. **性能测试**：验证在大量数据情况下的性能
4. **界面测试**：验证 UI 组件的正确渲染和交互

### 测试用例模板

以下是每个功能模块的测试用例模板：

```typescript
// 路由管理 API 测试用例
import { test, expect } from '@playwright/test';

test.describe('Routes API Integration', () => {
  test('should list all routes', async ({ page }) => {
    await page.goto('/en/dashboard/routes');
    await page.waitForSelector('[data-testid="routes-table"]');

    // 验证路由列表是否加载
    const routesCount = await page.locator('[data-testid="route-row"]').count();
    expect(routesCount).toBeGreaterThan(0);
  });

  test('should create a new route', async ({ page }) => {
    // 创建新路由的测试步骤
  });

  test('should view route details', async ({ page }) => {
    // 查看路由详情的测试步骤
  });

  test('should update a route', async ({ page }) => {
    // 更新路由的测试步骤
  });

  test('should delete a route', async ({ page }) => {
    // 删除路由的测试步骤
  });

  test('should handle errors gracefully', async ({ page }) => {
    // 错误处理测试步骤
  });
});
```

### 测试执行流程

1. 启动 APIX 后端服务
2. 启动 UI 开发服务
3. 运行 Playwright 测试用例
4. 生成测试报告
5. 分析测试结果，并更新实现状态

### 测试成功标准

每个功能模块的测试成功标准如下：

1. 所有测试用例必须通过
2. 界面响应时间必须在可接受范围内（例如，列表页面加载时间不超过 2 秒）
3. 错误处理必须正确，并提供友好的错误信息
4. UI 组件必须正确渲染，并与设计规范一致

测试通过后，将在 api.md 文档中更新相应功能模块的实现状态和测试状态。

## 注意事项

1. 所有 API 调用应该使用 `ApiClient` 类或其子类，不要直接使用 `fetch`
2. 所有 API 调用应该处理错误，并提供友好的错误信息
3. 所有 API 调用应该处理加载状态，提供良好的用户体验
4. 所有 API 调用应该处理空数据，确保 UI 不会崩溃
5. 所有 API 调用应该处理权限问题，确保用户只能访问他们有权限的资源

## 实现计划和时间表

以下是各功能模块的实现计划和时间表，包括开发、测试和部署的时间安排。

### 第一阶段：基础设施和核心功能（第 1-2 周）

| 功能模块 | 开发时间 | 测试时间 | 负责人 |
|---------|---------|---------|--------|
| 基础 API 客户端 | 2天 | 1天 | TBD |
| 路由管理 API | 3天 | 2天 | TBD |
| 插件管理 API | 3天 | 2天 | TBD |

**具体任务：**

1. 基础 API 客户端
   - 实现基础 `ApiClient` 类
   - 添加错误处理和重试机制
   - 添加认证和授权机制

2. 路由管理 API
   - 实现 `RoutesApiClient` 类
   - 更新路由列表页面
   - 更新路由详情页面
   - 更新路由创建/编辑页面

3. 插件管理 API
   - 实现 `PluginApiClient` 类
   - 更新插件列表页面
   - 更新插件详情页面
   - 更新插件创建/编辑页面

### 第二阶段：系统管理功能（第 3-4 周）

| 功能模块 | 开发时间 | 测试时间 | 负责人 |
|---------|---------|---------|--------|
| 服务管理 API | 3天 | 2天 | TBD |
| 配置管理 API | 2天 | 1天 | TBD |
| 系统指标 API | 2天 | 1天 | TBD |

**具体任务：**

1. 服务管理 API
   - 实现 `ServicesApiClient` 类
   - 更新服务列表页面
   - 更新服务详情页面
   - 更新服务创建/编辑页面

2. 配置管理 API
   - 实现 `ConfigApiClient` 类
   - 更新配置页面

3. 系统指标 API
   - 实现 `MetricsApiClient` 类
   - 更新指标仪表盘页面
   - 更新健康状态页面

### 第三阶段：AI 功能和安全管理（第 5-6 周）

| 功能模块 | 开发时间 | 测试时间 | 负责人 |
|---------|---------|---------|--------|
| AI 模型管理 API | 3天 | 2天 | TBD |
| AI 路由规则 API | 3天 | 2天 | TBD |
| API 密钥管理 API | 2天 | 1天 | TBD |

**具体任务：**

1. AI 模型管理 API
   - 实现 `AIModelsApiClient` 类
   - 更新 AI 模型列表页面
   - 更新 AI 模型详情页面
   - 更新 AI 模型创建/编辑页面

2. AI 路由规则 API
   - 实现 `AIRoutingApiClient` 类
   - 更新 AI 路由规则列表页面
   - 更新 AI 路由规则详情页面
   - 更新 AI 路由规则创建/编辑页面

3. API 密钥管理 API
   - 实现 `AuthApiClient` 类
   - 更新 API 密钥列表页面
   - 更新 API 密钥创建页面

### 第四阶段：整合测试和优化（第 7-8 周）

| 任务 | 时间 | 负责人 |
|---------|---------|--------|
| 端到端测试 | 5天 | TBD |
| 性能优化 | 3天 | TBD |
| 文档更新 | 2天 | TBD |

**具体任务：**

1. 端到端测试
   - 编写全面的 Playwright 测试用例
   - 运行测试并修复问题
   - 生成测试报告

2. 性能优化
   - 优化 API 调用频率
   - 添加缓存机制
   - 优化界面响应速度

3. 文档更新
   - 更新 API 文档
   - 更新实现状态
   - 编写最终报告

## 后端 API 端点汇总

### 路由管理
- `GET    /admin/routes`
- `POST   /admin/routes`
- `GET    /admin/routes/:id`
- `PUT    /admin/routes/:id`
- `DELETE /admin/routes/:id`

### 插件管理
- `GET    /admin/plugins`
- `GET    /admin/plugins/:id`
- `POST   /admin/plugins`
- `PUT    /admin/plugins/:id`
- `DELETE /admin/plugins/:id`
- `POST   /admin/plugins/:id/enable`
- `POST   /admin/plugins/:id/disable`
- `POST   /admin/plugins/:id/reload`
- `GET    /admin/plugins/types`

### 服务管理
- `GET    /admin/services`
- `POST   /admin/services`
- `GET    /admin/services/:id`
- `PUT    /admin/services/:id`
- `DELETE /admin/services/:id`
- `GET    /admin/services/:id/health`

### 配置管理
- `GET    /admin/config`
- `PUT    /admin/config`

### 系统指标
- `GET    /admin/metrics`
- `GET    /admin/metrics/cpu`
- `GET    /admin/metrics/memory`
- `GET    /admin/metrics/threads`
- `GET    /admin/metrics/jvm`
- `GET    /admin/metrics/os`
- `GET    /admin/health`

### AI 模型管理
- `GET    /admin/ai/models`
- `POST   /admin/ai/models`
- `GET    /admin/ai/models/:id`
- `PUT    /admin/ai/models/:id`
- `DELETE /admin/ai/models/:id`
- `POST   /admin/ai/models/:id/enable`
- `POST   /admin/ai/models/:id/disable`

### AI 路由规则
- `GET    /admin/ai/routing/rules`
- `POST   /admin/ai/routing/rules`
- `GET    /admin/ai/routing/rules/:id`
- `PUT    /admin/ai/routing/rules/:id`
- `DELETE /admin/ai/routing/rules/:id`
- `POST   /admin/ai/routing/rules/:id/enable`
- `POST   /admin/ai/routing/rules/:id/disable`

### AI 缓存管理
- `GET    /admin/ai/cache/stats`
- `POST   /admin/ai/cache/clear`

### API 密钥管理
- `GET    /admin/auth/api-keys`
- `POST   /admin/auth/api-keys`
- `DELETE /admin/auth/api-keys/:id`

### 用户认证
- `POST   /admin/auth/login`
- `POST   /admin/auth/logout`
- `GET    /admin/auth/me`
- `POST   /admin/auth/register`

## 总结

本文档详细规划了 APIX UI 与 APIX 后端 API 的对接实现方案。通过分阶段实现，我们将把当前使用模拟数据的 UI 完全迁移到使用真实后端 API 的生产就绪系统。

实现过程将遵循以下原则：

1. **渐进式实现**：分模块、分阶段实现，确保每个阶段都有可交付的成果
2. **测试验证**：每个功能模块实现后都进行全面的测试验证
3. **文档更新**：及时更新文档，记录实现状态和测试结果
4. **性能优化**：在功能实现的基础上进行性能优化

通过这个实现计划，我们将在 8 周内完成所有功能模块的实现和测试，最终交付一个完全集成了后端 API 的高质量 UI 系统。
