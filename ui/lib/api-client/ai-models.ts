/**
 * AI 模型管理 API 客户端
 */
import { ApiClient } from "./base";

/**
 * AI 模型接口
 */
export interface AIModel {
  id: string;
  name: string;
  provider: string;
  description?: string;
  maxTokens?: number;
  enabled: boolean;
  priority?: number;
  costPerToken?: number;
  capabilities?: string[];
  contextWindow?: number;
  apiKey?: string;
  baseUrl?: string;
  version?: string;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * AI 模型提供商接口
 */
export interface AIProvider {
  id: string;
  name: string;
  description?: string;
  apiKeyRequired: boolean;
  baseUrlConfigurable: boolean;
  supportedModels: string[];
  defaultModel?: string;
  logoUrl?: string;
}

/**
 * AI 路由规则接口
 */
export interface AIRoutingRule {
  id: string;
  name: string;
  priority: number;
  condition: {
    type: string;
    pattern: string;
    contentTypes?: string[];
    requestTypes?: string[];
    headers?: Record<string, string>;
    parameters?: Record<string, string>;
  };
  targetModel: string;
  enabled: boolean;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * AI 模型列表响应接口
 */
export interface AIModelsResponse {
  models: AIModel[];
  total: number;
  page: number;
  pageSize: number;
}

/**
 * AI 模型详情响应接口
 */
export interface AIModelResponse {
  model: AIModel;
}

/**
 * AI 模型操作响应接口
 */
export interface AIModelActionResponse {
  success: boolean;
  model: AIModel;
  message?: string;
}

/**
 * AI 模型删除响应接口
 */
export interface AIModelDeleteResponse {
  success: boolean;
  message?: string;
}

/**
 * AI 提供商列表响应接口
 */
export interface AIProvidersResponse {
  providers: AIProvider[];
}

/**
 * AI 路由规则列表响应接口
 */
export interface AIRoutingRulesResponse {
  rules: AIRoutingRule[];
  total: number;
  page: number;
  pageSize: number;
}

/**
 * AI 路由规则详情响应接口
 */
export interface AIRoutingRuleResponse {
  rule: AIRoutingRule;
}

/**
 * AI 路由规则操作响应接口
 */
export interface AIRoutingRuleActionResponse {
  success: boolean;
  rule: AIRoutingRule;
  message?: string;
}

/**
 * AI 路由规则删除响应接口
 */
export interface AIRoutingRuleDeleteResponse {
  success: boolean;
  message?: string;
}

/**
 * AI 模型查询参数接口
 */
export interface AIModelQueryParams {
  page?: number;
  pageSize?: number;
  search?: string;
  provider?: string;
  enabled?: boolean;
  sortBy?: string;
  sortOrder?: 'asc' | 'desc';
}

/**
 * AI 路由规则查询参数接口
 */
export interface AIRoutingRuleQueryParams {
  page?: number;
  pageSize?: number;
  search?: string;
  targetModel?: string;
  enabled?: boolean;
  sortBy?: string;
  sortOrder?: 'asc' | 'desc';
}

/**
 * AI 模型管理 API 客户端类
 */
export class AIModelsApiClient extends ApiClient {
  /**
   * 获取所有 AI 模型
   * @param params 查询参数
   * @returns AI 模型列表响应
   */
  async getModels(params?: AIModelQueryParams) {
    return this.get<AIModelsResponse>('/admin/ai/models', params);
  }

  /**
   * 根据 ID 获取 AI 模型
   * @param id 模型 ID
   * @returns AI 模型详情响应
   */
  async getModel(id: string) {
    return this.get<AIModelResponse>(`/admin/ai/models/${id}`);
  }

  /**
   * 创建新的 AI 模型
   * @param model 模型数据
   * @returns AI 模型创建响应
   */
  async createModel(model: Omit<AIModel, 'id' | 'enabled' | 'createdAt' | 'updatedAt'>) {
    return this.post<AIModelActionResponse>('/admin/ai/models', model);
  }

  /**
   * 更新 AI 模型
   * @param id 模型 ID
   * @param model 模型数据
   * @returns AI 模型更新响应
   */
  async updateModel(id: string, model: Partial<Omit<AIModel, 'id' | 'createdAt' | 'updatedAt'>>) {
    return this.put<AIModelActionResponse>(`/admin/ai/models/${id}`, model);
  }

  /**
   * 删除 AI 模型
   * @param id 模型 ID
   * @returns AI 模型删除响应
   */
  async deleteModel(id: string) {
    return this.delete<AIModelDeleteResponse>(`/admin/ai/models/${id}`);
  }

  /**
   * 启用 AI 模型
   * @param id 模型 ID
   * @returns AI 模型启用响应
   */
  async enableModel(id: string) {
    return this.post<AIModelActionResponse>(`/admin/ai/models/${id}/enable`, {});
  }

  /**
   * 禁用 AI 模型
   * @param id 模型 ID
   * @returns AI 模型禁用响应
   */
  async disableModel(id: string) {
    return this.post<AIModelActionResponse>(`/admin/ai/models/${id}/disable`, {});
  }

  /**
   * 获取所有 AI 提供商
   * @returns AI 提供商列表响应
   */
  async getProviders() {
    return this.get<AIProvidersResponse>('/admin/ai/providers');
  }

  /**
   * 获取所有 AI 路由规则
   * @param params 查询参数
   * @returns AI 路由规则列表响应
   */
  async getRoutingRules(params?: AIRoutingRuleQueryParams) {
    return this.get<AIRoutingRulesResponse>('/admin/ai/routing/rules', params);
  }

  /**
   * 根据 ID 获取 AI 路由规则
   * @param id 规则 ID
   * @returns AI 路由规则详情响应
   */
  async getRoutingRule(id: string) {
    return this.get<AIRoutingRuleResponse>(`/admin/ai/routing/rules/${id}`);
  }

  /**
   * 创建新的 AI 路由规则
   * @param rule 规则数据
   * @returns AI 路由规则创建响应
   */
  async createRoutingRule(rule: Omit<AIRoutingRule, 'id' | 'enabled' | 'createdAt' | 'updatedAt'>) {
    return this.post<AIRoutingRuleActionResponse>('/admin/ai/routing/rules', rule);
  }

  /**
   * 更新 AI 路由规则
   * @param id 规则 ID
   * @param rule 规则数据
   * @returns AI 路由规则更新响应
   */
  async updateRoutingRule(id: string, rule: Partial<Omit<AIRoutingRule, 'id' | 'createdAt' | 'updatedAt'>>) {
    return this.put<AIRoutingRuleActionResponse>(`/admin/ai/routing/rules/${id}`, rule);
  }

  /**
   * 删除 AI 路由规则
   * @param id 规则 ID
   * @returns AI 路由规则删除响应
   */
  async deleteRoutingRule(id: string) {
    return this.delete<AIRoutingRuleDeleteResponse>(`/admin/ai/routing/rules/${id}`);
  }

  /**
   * 启用 AI 路由规则
   * @param id 规则 ID
   * @returns AI 路由规则启用响应
   */
  async enableRoutingRule(id: string) {
    return this.post<AIRoutingRuleActionResponse>(`/admin/ai/routing/rules/${id}/enable`, {});
  }

  /**
   * 禁用 AI 路由规则
   * @param id 规则 ID
   * @returns AI 路由规则禁用响应
   */
  async disableRoutingRule(id: string) {
    return this.post<AIRoutingRuleActionResponse>(`/admin/ai/routing/rules/${id}/disable`, {});
  }

  /**
   * 测试 AI 模型连接
   * @param id 模型 ID
   * @returns 测试响应
   */
  async testModelConnection(id: string) {
    return this.post<{ success: boolean; message?: string }>(`/admin/ai/models/${id}/test`, {});
  }
}

// 创建单例实例
export const aiModelsApi = new AIModelsApiClient();
