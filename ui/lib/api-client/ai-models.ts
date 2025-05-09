/**
 * AI Models API client
 */
import { ApiClient } from "./base";

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
}

export interface AIRoutingRule {
  id: string;
  name: string;
  priority: number;
  condition: {
    type: string;
    pattern: string;
    contentTypes: string[];
    requestTypes: string[];
  };
  targetModel: string;
  enabled: boolean;
}

/**
 * AI Models-specific API client
 */
export class AIModelsApiClient extends ApiClient {
  /**
   * Get all AI models
   */
  async getModels() {
    return this.get<{ models: AIModel[] }>('/admin/ai/models');
  }

  /**
   * Get an AI model by ID
   */
  async getModel(id: string) {
    return this.get<{ model: AIModel }>(`/admin/ai/models/${id}`);
  }

  /**
   * Create a new AI model
   */
  async createModel(model: Omit<AIModel, 'id' | 'enabled'>) {
    return this.post<{ success: boolean; model: AIModel }>('/admin/ai/models', model);
  }

  /**
   * Update an AI model
   */
  async updateModel(id: string, model: Partial<AIModel>) {
    return this.put<{ success: boolean; model: AIModel }>(`/admin/ai/models/${id}`, model);
  }

  /**
   * Delete an AI model
   */
  async deleteModel(id: string) {
    return this.delete<{ success: boolean }>(`/admin/ai/models/${id}`);
  }

  /**
   * Enable an AI model
   */
  async enableModel(id: string) {
    return this.post<{ success: boolean; model: AIModel }>(`/admin/ai/models/${id}/enable`);
  }

  /**
   * Disable an AI model
   */
  async disableModel(id: string) {
    return this.post<{ success: boolean; model: AIModel }>(`/admin/ai/models/${id}/disable`);
  }

  /**
   * Get all AI routing rules
   */
  async getRoutingRules() {
    return this.get<{ rules: AIRoutingRule[] }>('/admin/ai/routing/rules');
  }

  /**
   * Get an AI routing rule by ID
   */
  async getRoutingRule(id: string) {
    return this.get<{ rule: AIRoutingRule }>(`/admin/ai/routing/rules/${id}`);
  }

  /**
   * Create a new AI routing rule
   */
  async createRoutingRule(rule: Omit<AIRoutingRule, 'id' | 'enabled'>) {
    return this.post<{ success: boolean; rule: AIRoutingRule }>('/admin/ai/routing/rules', rule);
  }

  /**
   * Update an AI routing rule
   */
  async updateRoutingRule(id: string, rule: Partial<AIRoutingRule>) {
    return this.put<{ success: boolean; rule: AIRoutingRule }>(`/admin/ai/routing/rules/${id}`, rule);
  }

  /**
   * Delete an AI routing rule
   */
  async deleteRoutingRule(id: string) {
    return this.delete<{ success: boolean }>(`/admin/ai/routing/rules/${id}`);
  }

  /**
   * Enable an AI routing rule
   */
  async enableRoutingRule(id: string) {
    return this.post<{ success: boolean; rule: AIRoutingRule }>(`/admin/ai/routing/rules/${id}/enable`);
  }

  /**
   * Disable an AI routing rule
   */
  async disableRoutingRule(id: string) {
    return this.post<{ success: boolean; rule: AIRoutingRule }>(`/admin/ai/routing/rules/${id}/disable`);
  }
}

// Create singleton instance
export const aiModelsApi = new AIModelsApiClient();
