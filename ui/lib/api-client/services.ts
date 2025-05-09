/**
 * Services API client
 */
import { ApiClient } from "./base";

export interface Service {
  id: string;
  name: string;
  url: string;
  protocol?: string;
  port?: number;
  path?: string;
  retries?: number;
  timeout?: number;
  enabled: boolean;
  description?: string;
}

export interface ServiceHealth {
  status: 'UP' | 'DOWN' | 'UNKNOWN';
  timestamp: number;
  details?: Record<string, any>;
}

/**
 * Services-specific API client
 */
export class ServicesApiClient extends ApiClient {
  /**
   * Get all services
   */
  async getServices() {
    return this.get<{ services: Service[] }>('/admin/services');
  }

  /**
   * Get a service by ID
   */
  async getService(id: string) {
    return this.get<{ service: Service }>(`/admin/services/${id}`);
  }

  /**
   * Create a new service
   */
  async createService(service: Omit<Service, 'id' | 'enabled'>) {
    return this.post<{ success: boolean; service: Service }>('/admin/services', service);
  }

  /**
   * Update a service
   */
  async updateService(id: string, service: Partial<Service>) {
    return this.put<{ success: boolean; service: Service }>(`/admin/services/${id}`, service);
  }

  /**
   * Delete a service
   */
  async deleteService(id: string) {
    return this.delete<{ success: boolean }>(`/admin/services/${id}`);
  }

  /**
   * Get service health
   */
  async getServiceHealth(id: string) {
    return this.get<ServiceHealth>(`/admin/services/${id}/health`);
  }
}

// Create singleton instance
export const servicesApi = new ServicesApiClient();
