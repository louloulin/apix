/**
 * Plugins API client
 */
import { ApiClient } from "./base";

// Plugin types
export type PluginStatus = 'enabled' | 'disabled' | 'error';

export interface Plugin {
  id: string;
  type: string;
  config: Record<string, any>;
  status: PluginStatus;
  version?: string;
}

export interface PluginType {
  id: string;
  name: string;
  description: string;
  configSchema?: Record<string, any>;
  defaultConfig?: Record<string, any>;
}

/**
 * Plugin-specific API client
 */
export class PluginApiClient extends ApiClient {
  /**
   * Get all plugins
   */
  async getPlugins() {
    return this.get<{ plugins: Plugin[] }>('/admin/plugins');
  }

  /**
   * Get a plugin by ID
   */
  async getPlugin(id: string) {
    return this.get<{ plugin: Plugin }>(`/admin/plugins/${id}`);
  }

  /**
   * Create a new plugin
   */
  async createPlugin(plugin: Omit<Plugin, 'status'>) {
    return this.post<{ plugin: Plugin }>('/admin/plugins', plugin);
  }

  /**
   * Update a plugin
   */
  async updatePlugin(id: string, plugin: Partial<Plugin>) {
    return this.put<{ plugin: Plugin }>(`/admin/plugins/${id}`, plugin);
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
    return this.post<{ success: boolean; message: string; plugin: Plugin }>(`/admin/plugins/${id}/reload`);
  }

  /**
   * Get available plugin types
   */
  async getPluginTypes() {
    return this.get<{ types: PluginType[] }>('/admin/plugins/types');
  }
}

// Create singleton instance
export const pluginApi = new PluginApiClient();
