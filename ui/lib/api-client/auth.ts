/**
 * 认证和 API 密钥管理 API 客户端
 */
import { ApiClient } from "./base";

/**
 * 用户接口
 */
export interface User {
  username: string;
  role: string;
  email?: string;
  firstName?: string;
  lastName?: string;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * 登录响应接口
 */
export interface LoginResponse {
  success: boolean;
  token: string;
  user: User;
}

/**
 * API 密钥接口
 */
export interface ApiKey {
  id: string;
  key: string;
  name: string;
  scopes: string[];
  enabled: boolean;
  createdAt: string;
  expiresAt?: string;
  lastUsedAt?: string;
  description?: string;
  createdBy?: string;
}

/**
 * API 密钥列表响应接口
 */
export interface ApiKeysResponse {
  keys: ApiKey[];
  total: number;
  page: number;
  pageSize: number;
}

/**
 * API 密钥创建响应接口
 */
export interface ApiKeyCreateResponse {
  success: boolean;
  key: ApiKey;
  message?: string;
}

/**
 * API 密钥删除响应接口
 */
export interface ApiKeyDeleteResponse {
  success: boolean;
  message?: string;
}

/**
 * API 密钥查询参数接口
 */
export interface ApiKeyQueryParams {
  page?: number;
  pageSize?: number;
  search?: string;
  scope?: string;
  enabled?: boolean;
  sortBy?: string;
  sortOrder?: 'asc' | 'desc';
}

/**
 * 认证和 API 密钥管理 API 客户端类
 */
export class AuthApiClient extends ApiClient {
  /**
   * 使用用户名和密码登录
   * @param username 用户名
   * @param password 密码
   * @returns 登录响应
   */
  async login(username: string, password: string) {
    return this.post<LoginResponse>('/admin/auth/login', { username, password });
  }

  /**
   * 注销当前用户
   * @returns 注销响应
   */
  async logout() {
    return this.post<{ success: boolean; message: string }>('/admin/auth/logout');
  }

  /**
   * 获取当前用户
   * @returns 当前用户信息
   */
  async getCurrentUser() {
    return this.get<{ user: User }>('/admin/auth/me');
  }

  /**
   * 注册新用户
   * @param username 用户名
   * @param password 密码
   * @returns 注册响应
   */
  async register(username: string, password: string) {
    return this.post<{ success: boolean; message: string }>('/admin/auth/register', { username, password });
  }

  /**
   * 修改密码
   * @param currentPassword 当前密码
   * @param newPassword 新密码
   * @returns 修改密码响应
   */
  async changePassword(currentPassword: string, newPassword: string) {
    return this.post<{ success: boolean; message: string }>('/admin/auth/change-password', { currentPassword, newPassword });
  }

  /**
   * 获取所有 API 密钥
   * @param params 查询参数
   * @returns API 密钥列表响应
   */
  async getApiKeys(params?: ApiKeyQueryParams) {
    return this.get<ApiKeysResponse>('/admin/auth/api-keys', params);
  }

  /**
   * 创建新 API 密钥
   * @param name 密钥名称
   * @param scopes 密钥权限范围
   * @param expiresAt 过期时间（可选）
   * @param description 描述（可选）
   * @returns API 密钥创建响应
   */
  async createApiKey(name: string, scopes: string[], expiresAt?: string, description?: string) {
    return this.post<ApiKeyCreateResponse>('/admin/auth/api-keys', { name, scopes, expiresAt, description });
  }

  /**
   * 删除 API 密钥
   * @param id 密钥 ID
   * @returns API 密钥删除响应
   */
  async deleteApiKey(id: string) {
    return this.delete<ApiKeyDeleteResponse>(`/admin/auth/api-keys/${id}`);
  }

  /**
   * 获取可用的 API 密钥权限范围
   * @returns 权限范围列表
   */
  async getApiKeyScopes() {
    return this.get<{ scopes: Array<{ id: string; name: string; description?: string }> }>('/admin/auth/api-keys/scopes');
  }
}

// 创建单例实例
export const authApi = new AuthApiClient();
