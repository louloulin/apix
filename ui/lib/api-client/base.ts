/**
 * 基础 API 客户端
 * 提供与后端 API 交互的基本方法
 */

// API 基础 URL，从环境变量中获取，默认为本地开发环境
const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080';

// 请求超时时间（毫秒）
const DEFAULT_TIMEOUT = 30000;

// 重试次数
const DEFAULT_RETRY_COUNT = 3;

// 重试延迟（毫秒）
const DEFAULT_RETRY_DELAY = 1000;

/**
 * API 错误类
 */
export class ApiError extends Error {
  status: number;
  data?: any;

  constructor(message: string, status: number, data?: any) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.data = data;
  }
}

/**
 * 基础 API 客户端类
 */
export class ApiClient {
  private baseUrl: string;
  private timeout: number;
  private retryCount: number;
  private retryDelay: number;

  /**
   * 构造函数
   * @param baseUrl API 基础 URL
   * @param timeout 请求超时时间（毫秒）
   * @param retryCount 重试次数
   * @param retryDelay 重试延迟（毫秒）
   */
  constructor(
    baseUrl: string = API_BASE_URL,
    timeout: number = DEFAULT_TIMEOUT,
    retryCount: number = DEFAULT_RETRY_COUNT,
    retryDelay: number = DEFAULT_RETRY_DELAY
  ) {
    this.baseUrl = baseUrl;
    this.timeout = timeout;
    this.retryCount = retryCount;
    this.retryDelay = retryDelay;
  }

  /**
   * 获取认证头
   * @returns 认证头对象
   */
  private getAuthHeaders(): Record<string, string> {
    // 从 localStorage 获取 token
    const token = typeof window !== 'undefined' ? localStorage.getItem('auth_token') : null;
    return token ? { 'Authorization': `Bearer ${token}` } : {};
  }

  /**
   * 构建完整的 URL
   * @param path API 路径
   * @returns 完整的 URL
   */
  private buildUrl(path: string): string {
    // 确保 path 以 / 开头
    const normalizedPath = path.startsWith('/') ? path : `/${path}`;
    // 确保 baseUrl 不以 / 结尾
    const normalizedBaseUrl = this.baseUrl.endsWith('/')
      ? this.baseUrl.slice(0, -1)
      : this.baseUrl;

    return `${normalizedBaseUrl}${normalizedPath}`;
  }

  /**
   * 处理响应
   * @param response Fetch 响应对象
   * @returns 解析后的响应数据
   * @throws ApiError 当响应状态码不是 2xx 时
   */
  private async handleResponse<T>(response: Response): Promise<T> {
    // 检查响应状态码
    if (!response.ok) {
      let errorData;
      try {
        errorData = await response.json();
      } catch (e) {
        errorData = { message: 'Unknown error' };
      }

      throw new ApiError(
        errorData.message || `API error: ${response.status}`,
        response.status,
        errorData
      );
    }

    // 检查响应内容类型
    const contentType = response.headers.get('content-type');
    if (contentType && contentType.includes('application/json')) {
      return await response.json() as T;
    } else {
      return await response.text() as unknown as T;
    }
  }

  /**
   * 执行请求，支持重试
   * @param url 请求 URL
   * @param options 请求选项
   * @returns 响应数据
   */
  private async fetchWithRetry<T>(url: string, options: RequestInit): Promise<T> {
    let lastError: Error | null = null;

    // 尝试请求，最多重试 retryCount 次
    for (let attempt = 0; attempt <= this.retryCount; attempt++) {
      try {
        // 添加超时控制
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), this.timeout);

        const response = await fetch(url, {
          ...options,
          signal: controller.signal
        });

        clearTimeout(timeoutId);
        return await this.handleResponse<T>(response);
      } catch (error) {
        lastError = error as Error;

        // 如果是最后一次尝试，或者是中止错误（超时），则抛出错误
        if (
          attempt === this.retryCount ||
          (error instanceof DOMException && error.name === 'AbortError')
        ) {
          throw lastError;
        }

        // 否则等待一段时间后重试
        await new Promise(resolve => setTimeout(resolve, this.retryDelay));
      }
    }

    // 这里应该不会执行到，但为了类型安全
    throw lastError || new Error('Unknown error');
  }

  /**
   * 发送 GET 请求
   * @param path API 路径
   * @param params 查询参数
   * @returns 响应数据
   */
  async get<T>(path: string, params?: Record<string, string | number | boolean>): Promise<T> {
    // 构建 URL 和查询参数
    let url = this.buildUrl(path);

    if (params) {
      const queryParams = new URLSearchParams();
      Object.entries(params).forEach(([key, value]) => {
        queryParams.append(key, String(value));
      });
      url += `?${queryParams.toString()}`;
    }

    // 发送请求
    return this.fetchWithRetry<T>(url, {
      method: 'GET',
      headers: {
        'Accept': 'application/json',
        ...this.getAuthHeaders()
      }
    });
  }

  /**
   * 发送 POST 请求
   * @param path API 路径
   * @param data 请求数据
   * @returns 响应数据
   */
  async post<T>(path: string, data?: any): Promise<T> {
    const url = this.buildUrl(path);

    // 发送请求
    return this.fetchWithRetry<T>(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        ...this.getAuthHeaders()
      },
      body: data ? JSON.stringify(data) : undefined
    });
  }

  /**
   * 发送 PUT 请求
   * @param path API 路径
   * @param data 请求数据
   * @returns 响应数据
   */
  async put<T>(path: string, data: any): Promise<T> {
    const url = this.buildUrl(path);

    // 发送请求
    return this.fetchWithRetry<T>(url, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        ...this.getAuthHeaders()
      },
      body: JSON.stringify(data)
    });
  }

  /**
   * 发送 DELETE 请求
   * @param path API 路径
   * @returns 响应数据
   */
  async delete<T>(path: string): Promise<T> {
    const url = this.buildUrl(path);

    // 发送请求
    return this.fetchWithRetry<T>(url, {
      method: 'DELETE',
      headers: {
        'Accept': 'application/json',
        ...this.getAuthHeaders()
      }
    });
  }

  /**
   * 发送 PATCH 请求
   * @param path API 路径
   * @param data 请求数据
   * @returns 响应数据
   */
  async patch<T>(path: string, data: any): Promise<T> {
    const url = this.buildUrl(path);

    // 发送请求
    return this.fetchWithRetry<T>(url, {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        ...this.getAuthHeaders()
      },
      body: JSON.stringify(data)
    });
  }
}
