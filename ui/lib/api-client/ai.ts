/**
 * AI-specific API client
 */
import { ApiClient } from "./base";

/**
 * AI-specific API client
 */
export class AiApiClient extends ApiClient {
  /**
   * Get available AI models
   */
  async getModels() {
    return this.get<{ models: any[] }>('/admin/ai/models');
  }

  /**
   * Get AI usage statistics
   */
  async getUsage() {
    return this.get<{ usage: any }>('/admin/ai/usage');
  }

  /**
   * Get AI cache statistics
   */
  async getCacheStats() {
    return this.get<{ stats: any }>('/admin/ai/cache/stats');
  }

  /**
   * Clear AI cache
   */
  async clearCache(modelId?: string) {
    const path = modelId 
      ? `/admin/ai/cache/clear?modelId=${modelId}` 
      : '/admin/ai/cache/clear';
    
    return this.post<{ success: boolean; message: string; count: number }>(path);
  }

  /**
   * Get AI routing rules
   */
  async getRoutingRules() {
    return this.get<{ rules: any[] }>('/admin/ai/routing/rules');
  }
}
