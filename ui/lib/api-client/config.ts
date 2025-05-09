/**
 * 配置管理 API 客户端
 */
import { ApiClient } from "./base";

/**
 * 网关配置接口
 */
export interface GatewayConfig {
  gateway: {
    host: string;
    port: number;
    ssl?: {
      enabled: boolean;
      certPath?: string;
      keyPath?: string;
    };
    cors?: {
      enabled: boolean;
      allowedOrigins?: string[];
      allowedMethods?: string[];
      allowedHeaders?: string[];
      exposedHeaders?: string[];
      allowCredentials?: boolean;
      maxAge?: number;
    };
    compression?: {
      enabled: boolean;
      level?: number;
      minSize?: number;
    };
    requestTimeout?: number;
    idleTimeout?: number;
    maxHeaderSize?: number;
    maxBodySize?: number;
  };
  admin: {
    enabled: boolean;
    host: string;
    port: number;
    ssl?: {
      enabled: boolean;
      certPath?: string;
      keyPath?: string;
    };
    auth?: {
      enabled: boolean;
      type: string;
      users?: Array<{
        username: string;
        password: string;
        roles: string[];
      }>;
    };
  };
  logging: {
    level: string;
    file?: string;
    console: boolean;
    format?: string;
  };
  metrics?: {
    enabled: boolean;
    prometheus?: {
      enabled: boolean;
      path?: string;
    };
  };
  cluster?: {
    enabled: boolean;
    nodes?: string[];
    nodeName?: string;
  };
  plugins?: Array<{
    id: string;
    type: string;
    config: Record<string, any>;
    enabled: boolean;
  }>;
  routes?: Array<{
    id: string;
    path: string;
    methods: string[];
    targetUrl: string;
    plugins: string[];
    enabled: boolean;
    priority: number;
  }>;
  services?: Array<{
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
  }>;
  ai?: {
    enabled: boolean;
    providers?: Record<string, {
      apiKey: string;
      baseUrl?: string;
      models?: string[];
      defaultModel?: string;
    }>;
    routing?: {
      enabled: boolean;
      rules?: Array<{
        id: string;
        priority: number;
        condition: Record<string, any>;
        targetModel: string;
        enabled: boolean;
      }>;
    };
    cache?: {
      enabled: boolean;
      ttl?: number;
      maxSize?: number;
    };
  };
}

/**
 * 配置响应接口
 */
export interface ConfigResponse {
  config: GatewayConfig;
}

/**
 * 配置更新响应接口
 */
export interface ConfigUpdateResponse {
  success: boolean;
  message?: string;
  config?: GatewayConfig;
}

/**
 * 配置管理 API 客户端类
 */
export class ConfigApiClient extends ApiClient {
  /**
   * 获取当前网关配置
   * @returns 配置响应
   */
  async getConfig() {
    return this.get<ConfigResponse>('/admin/config');
  }

  /**
   * 更新网关配置
   * @param config 配置数据
   * @returns 配置更新响应
   */
  async updateConfig(config: Partial<GatewayConfig>) {
    return this.put<ConfigUpdateResponse>('/admin/config', config);
  }

  /**
   * 重启网关
   * @returns 重启响应
   */
  async restartGateway() {
    return this.post<{ success: boolean; message?: string }>('/admin/restart', {});
  }

  /**
   * 重新加载配置
   * @returns 重新加载响应
   */
  async reloadConfig() {
    return this.post<{ success: boolean; message?: string }>('/admin/config/reload', {});
  }
}

// 创建单例实例
export const configApi = new ConfigApiClient();
