/**
 * 路由管理 API 客户端
 */
import { ApiClient } from "./base";

/**
 * 路由模型接口
 */
export interface Route {
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

/**
 * 路由列表响应接口
 */
export interface RoutesResponse {
  routes: Route[];
  total: number;
  page: number;
  pageSize: number;
}

/**
 * 路由详情响应接口
 */
export interface RouteResponse {
  route: Route;
}

/**
 * 路由创建/更新响应接口
 */
export interface RouteActionResponse {
  success: boolean;
  route: Route;
  message?: string;
}

/**
 * 路由删除响应接口
 */
export interface RouteDeleteResponse {
  success: boolean;
  message?: string;
}

/**
 * 路由查询参数接口
 */
export interface RouteQueryParams {
  page?: number;
  pageSize?: number;
  search?: string;
  type?: string;
  enabled?: boolean;
  sortBy?: string;
  sortOrder?: 'asc' | 'desc';
}

/**
 * 路由管理 API 客户端类
 */
export class RoutesApiClient extends ApiClient {
  /**
   * 获取所有路由
   * @param params 查询参数
   * @returns 路由列表响应
   */
  async getRoutes(params?: RouteQueryParams) {
    return this.get<RoutesResponse>('/admin/routes', params);
  }

  /**
   * 根据 ID 获取路由
   * @param id 路由 ID
   * @returns 路由详情响应
   */
  async getRoute(id: string) {
    return this.get<RouteResponse>(`/admin/routes/${id}`);
  }

  /**
   * 创建新路由
   * @param route 路由数据
   * @returns 路由创建响应
   */
  async createRoute(route: Omit<Route, 'id' | 'createdAt' | 'updatedAt'>) {
    return this.post<RouteActionResponse>('/admin/routes', route);
  }

  /**
   * 更新路由
   * @param id 路由 ID
   * @param route 路由数据
   * @returns 路由更新响应
   */
  async updateRoute(id: string, route: Partial<Omit<Route, 'id' | 'createdAt' | 'updatedAt'>>) {
    return this.put<RouteActionResponse>(`/admin/routes/${id}`, route);
  }

  /**
   * 删除路由
   * @param id 路由 ID
   * @returns 路由删除响应
   */
  async deleteRoute(id: string) {
    return this.delete<RouteDeleteResponse>(`/admin/routes/${id}`);
  }

  /**
   * 启用路由
   * @param id 路由 ID
   * @returns 路由启用响应
   */
  async enableRoute(id: string) {
    return this.post<RouteActionResponse>(`/admin/routes/${id}/enable`, {});
  }

  /**
   * 禁用路由
   * @param id 路由 ID
   * @returns 路由禁用响应
   */
  async disableRoute(id: string) {
    return this.post<RouteActionResponse>(`/admin/routes/${id}/disable`, {});
  }
}

// 创建单例实例
export const routesApi = new RoutesApiClient();
