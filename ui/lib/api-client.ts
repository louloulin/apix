/**
 * API client for the APIX backend
 */

const API_BASE_URL = process.env.API_BASE_URL || 'http://localhost:8080';

/**
 * Base API client with common functionality
 */
export class ApiClient {
  private baseUrl: string;

  constructor(baseUrl: string = API_BASE_URL) {
    this.baseUrl = baseUrl;
  }

  /**
   * Make a GET request to the API
   */
  async get<T>(path: string): Promise<T> {
    const response = await fetch(`${this.baseUrl}${path}`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
      },
    });

    if (!response.ok) {
      throw new Error(`API error: ${response.status}`);
    }

    return response.json() as Promise<T>;
  }

  /**
   * Make a POST request to the API
   */
  async post<T>(path: string, data?: any): Promise<T> {
    const response = await fetch(`${this.baseUrl}${path}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: data ? JSON.stringify(data) : undefined,
    });

    if (!response.ok) {
      throw new Error(`API error: ${response.status}`);
    }

    return response.json() as Promise<T>;
  }

  /**
   * Make a PUT request to the API
   */
  async put<T>(path: string, data: any): Promise<T> {
    const response = await fetch(`${this.baseUrl}${path}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(data),
    });

    if (!response.ok) {
      throw new Error(`API error: ${response.status}`);
    }

    return response.json() as Promise<T>;
  }

  /**
   * Make a DELETE request to the API
   */
  async delete<T>(path: string): Promise<T> {
    const response = await fetch(`${this.baseUrl}${path}`, {
      method: 'DELETE',
      headers: {
        'Content-Type': 'application/json',
      },
    });

    if (!response.ok) {
      throw new Error(`API error: ${response.status}`);
    }

    return response.json() as Promise<T>;
  }
}

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

/**
 * Plugin-specific API client
 */
export class PluginApiClient extends ApiClient {
  /**
   * Get all plugins
   */
  async getPlugins() {
    return this.get<{ plugins: any[] }>('/admin/plugins');
  }

  /**
   * Get a plugin by ID
   */
  async getPlugin(id: string) {
    return this.get<{ plugin: any }>(`/admin/plugins/${id}`);
  }

  /**
   * Create a new plugin
   */
  async createPlugin(plugin: any) {
    return this.post<{ plugin: any }>('/admin/plugins', plugin);
  }

  /**
   * Update a plugin
   */
  async updatePlugin(id: string, plugin: any) {
    return this.put<{ plugin: any }>(`/admin/plugins/${id}`, plugin);
  }

  /**
   * Delete a plugin
   */
  async deletePlugin(id: string) {
    return this.delete<{ success: boolean }>(`/admin/plugins/${id}`);
  }

  /**
   * Enable a plugin
   */
  async enablePlugin(id: string) {
    return this.post<{ success: boolean; message: string }>(`/admin/plugins/${id}/enable`);
  }

  /**
   * Disable a plugin
   */
  async disablePlugin(id: string) {
    return this.post<{ success: boolean; message: string }>(`/admin/plugins/${id}/disable`);
  }

  /**
   * Reload a plugin
   */
  async reloadPlugin(id: string) {
    return this.post<{ success: boolean; message: string; plugin: any }>(`/admin/plugins/${id}/reload`);
  }
}

// Create singleton instances
export const aiApi = new AiApiClient();
export const pluginApi = new PluginApiClient();
