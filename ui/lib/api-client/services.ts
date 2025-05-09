/**
 * 服务管理 API 客户端
 */
import { ApiClient } from "./base";

/**
 * 服务模型接口
 */
export interface Service {
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

/**
 * 服务健康状态接口
 */
export interface ServiceHealth {
  status: 'UP' | 'DOWN' | 'UNKNOWN';
  timestamp: number;
  details?: Record<string, any>;
}

/**
 * 服务列表响应接口
 */
export interface ServicesResponse {
  services: Service[];
  total: number;
  page: number;
  pageSize: number;
}

/**
 * 服务详情响应接口
 */
export interface ServiceResponse {
  service: Service;
}

/**
 * 服务操作响应接口
 */
export interface ServiceActionResponse {
  success: boolean;
  service: Service;
  message?: string;
}

/**
 * 服务删除响应接口
 */
export interface ServiceDeleteResponse {
  success: boolean;
  message?: string;
}

/**
 * 服务查询参数接口
 */
export interface ServiceQueryParams {
  page?: number;
  pageSize?: number;
  search?: string;
  enabled?: boolean;
  sortBy?: string;
  sortOrder?: 'asc' | 'desc';
}

/**
 * 服务管理 API 客户端类
 */
export class ServicesApiClient extends ApiClient {
  /**
   * 获取所有服务
   * @param params 查询参数
   * @returns 服务列表响应
   */
  async getServices(params?: ServiceQueryParams) {
    return this.get<ServicesResponse>('/admin/services', params);
  }

  /**
   * 根据 ID 获取服务
   * @param id 服务 ID
   * @returns 服务详情响应
   */
  async getService(id: string) {
    return this.get<ServiceResponse>(`/admin/services/${id}`);
  }

  /**
   * 创建新服务
   * @param service 服务数据
   * @returns 服务创建响应
   */
  async createService(service: Omit<Service, 'id' | 'enabled' | 'createdAt' | 'updatedAt'>) {
    return this.post<ServiceActionResponse>('/admin/services', service);
  }

  /**
   * 更新服务
   * @param id 服务 ID
   * @param service 服务数据
   * @returns 服务更新响应
   */
  async updateService(id: string, service: Partial<Omit<Service, 'id' | 'createdAt' | 'updatedAt'>>) {
    return this.put<ServiceActionResponse>(`/admin/services/${id}`, service);
  }

  /**
   * 删除服务
   * @param id 服务 ID
   * @returns 服务删除响应
   */
  async deleteService(id: string) {
    return this.delete<ServiceDeleteResponse>(`/admin/services/${id}`);
  }

  /**
   * 获取服务健康状态
   * @param id 服务 ID
   * @returns 服务健康状态
   */
  async getServiceHealth(id: string) {
    return this.get<ServiceHealth>(`/admin/services/${id}/health`);
  }

  /**
   * 启用服务
   * @param id 服务 ID
   * @returns 服务启用响应
   */
  async enableService(id: string) {
    return this.post<ServiceActionResponse>(`/admin/services/${id}/enable`, {});
  }

  /**
   * 禁用服务
   * @param id 服务 ID
   * @returns 服务禁用响应
   */
  async disableService(id: string) {
    return this.post<ServiceActionResponse>(`/admin/services/${id}/disable`, {});
  }
}

// 创建单例实例
export const servicesApi = new ServicesApiClient();
