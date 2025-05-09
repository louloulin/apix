/**
 * 插件管理 API 客户端
 */
import { ApiClient } from "./base";

/**
 * 插件状态类型
 */
export type PluginStatus = 'enabled' | 'disabled' | 'error';

/**
 * 插件模型接口
 */
export interface Plugin {
  id: string;
  type: string;
  config: Record<string, any>;
  status: PluginStatus;
  version?: string;
  name?: string;
  description?: string;
  author?: string;
  repository?: string;
  dependencies?: string[];
  createdAt?: string;
  updatedAt?: string;
}

/**
 * 插件类型接口
 */
export interface PluginType {
  id: string;
  name: string;
  description: string;
  configSchema?: Record<string, any>;
  defaultConfig?: Record<string, any>;
  version?: string;
  author?: string;
  repository?: string;
}

/**
 * 插件列表响应接口
 */
export interface PluginsResponse {
  plugins: Plugin[];
  total: number;
  page: number;
  pageSize: number;
}

/**
 * 插件详情响应接口
 */
export interface PluginResponse {
  plugin: Plugin;
}

/**
 * 插件类型列表响应接口
 */
export interface PluginTypesResponse {
  types: PluginType[];
}

/**
 * 插件操作响应接口
 */
export interface PluginActionResponse {
  success: boolean;
  plugin?: Plugin;
  message?: string;
}

/**
 * 插件删除响应接口
 */
export interface PluginDeleteResponse {
  success: boolean;
  message?: string;
}

/**
 * 插件查询参数接口
 */
export interface PluginQueryParams {
  page?: number;
  pageSize?: number;
  search?: string;
  type?: string;
  status?: PluginStatus;
  sortBy?: string;
  sortOrder?: 'asc' | 'desc';
}

/**
 * 插件管理 API 客户端类
 */
export class PluginApiClient extends ApiClient {
  /**
   * 获取所有插件
   * @param params 查询参数
   * @returns 插件列表响应
   */
  async getPlugins(params?: PluginQueryParams) {
    return this.get<PluginsResponse>('/admin/plugins', params);
  }

  /**
   * 根据 ID 获取插件
   * @param id 插件 ID
   * @returns 插件详情响应
   */
  async getPlugin(id: string) {
    return this.get<PluginResponse>(`/admin/plugins/${id}`);
  }

  /**
   * 创建新插件
   * @param plugin 插件数据
   * @returns 插件创建响应
   */
  async createPlugin(plugin: Omit<Plugin, 'id' | 'status' | 'createdAt' | 'updatedAt'>) {
    return this.post<PluginActionResponse>('/admin/plugins', plugin);
  }

  /**
   * 更新插件
   * @param id 插件 ID
   * @param plugin 插件数据
   * @returns 插件更新响应
   */
  async updatePlugin(id: string, plugin: Partial<Omit<Plugin, 'id' | 'createdAt' | 'updatedAt'>>) {
    return this.put<PluginActionResponse>(`/admin/plugins/${id}`, plugin);
  }

  /**
   * 删除插件
   * @param id 插件 ID
   * @returns 插件删除响应
   */
  async deletePlugin(id: string) {
    return this.delete<PluginDeleteResponse>(`/admin/plugins/${id}`);
  }

  /**
   * 启用插件
   * @param id 插件 ID
   * @returns 插件启用响应
   */
  async enablePlugin(id: string) {
    return this.post<PluginActionResponse>(`/admin/plugins/${id}/enable`, {});
  }

  /**
   * 禁用插件
   * @param id 插件 ID
   * @returns 插件禁用响应
   */
  async disablePlugin(id: string) {
    return this.post<PluginActionResponse>(`/admin/plugins/${id}/disable`, {});
  }

  /**
   * 重新加载插件
   * @param id 插件 ID
   * @returns 插件重新加载响应
   */
  async reloadPlugin(id: string) {
    return this.post<PluginActionResponse>(`/admin/plugins/${id}/reload`, {});
  }

  /**
   * 获取可用的插件类型
   * @returns 插件类型列表响应
   */
  async getPluginTypes() {
    return this.get<PluginTypesResponse>('/admin/plugins/types');
  }
}

// 创建单例实例
export const pluginApi = new PluginApiClient();
