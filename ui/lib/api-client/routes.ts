/**
 * Routes API client
 */
import { ApiClient } from "./base";

export interface Route {
  id: string;
  path: string;
  target: string;
  methods?: string[];
  plugins?: string[];
  enabled: boolean;
  priority?: number;
  description?: string;
}

/**
 * Routes-specific API client
 */
export class RoutesApiClient extends ApiClient {
  /**
   * Get all routes
   */
  async getRoutes() {
    return this.get<{ routes: Route[] }>('/admin/routes');
  }

  /**
   * Get a route by ID
   */
  async getRoute(id: string) {
    return this.get<{ route: Route }>(`/admin/routes/${id}`);
  }

  /**
   * Create a new route
   */
  async createRoute(route: Omit<Route, 'id' | 'enabled'>) {
    return this.post<{ success: boolean; route: Route }>('/admin/routes', route);
  }

  /**
   * Update a route
   */
  async updateRoute(id: string, route: Partial<Route>) {
    return this.put<{ success: boolean; route: Route }>(`/admin/routes/${id}`, route);
  }

  /**
   * Delete a route
   */
  async deleteRoute(id: string) {
    return this.delete<{ success: boolean }>(`/admin/routes/${id}`);
  }
}

// Create singleton instance
export const routesApi = new RoutesApiClient();
