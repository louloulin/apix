/**
 * Configuration API client
 */
import { ApiClient } from "./base";

/**
 * Configuration-specific API client
 */
export class ConfigApiClient extends ApiClient {
  /**
   * Get the current gateway configuration
   */
  async getConfig() {
    return this.get<Record<string, any>>('/admin/config');
  }

  /**
   * Update the gateway configuration
   */
  async updateConfig(config: Record<string, any>) {
    return this.put<{ success: boolean }>('/admin/config', config);
  }
}

// Create singleton instance
export const configApi = new ConfigApiClient();
