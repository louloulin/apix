/**
 * Dashboard API 客户端
 */
import { ApiClient } from "./base";

/**
 * 仪表盘统计数据接口
 */
export interface DashboardStats {
  totalRequests: number;
  successRate: number;
  avgResponseTime: number;
  activeRoutes: number;
  activePlugins: number;
  errorRate: number;
}

/**
 * 仪表盘事件接口
 */
export interface DashboardEvent {
  id: string;
  title: string;
  description: string;
  time: string;
  timestamp: number;
  type: string;
}

/**
 * 路由统计数据接口
 */
export interface RouteStats {
  id: string;
  name: string;
  requests: number;
  successRate: number;
}

/**
 * LLM 使用统计数据接口
 */
export interface LlmStats {
  totalTokens: number;
  avgTokensPerRequest: number;
  mostUsedModel: string;
  estimatedCost: number;
}

/**
 * 流量数据点接口
 */
export interface TrafficDataPoint {
  date: string;
  requests: number;
  responseTime: number;
  successRate: number;
}

/**
 * LLM 使用数据点接口
 */
export interface LlmUsageDataPoint {
  date: string;
  requests: number;
  tokens: number;
  provider: string;
}

/**
 * 仪表盘数据接口
 */
export interface DashboardData {
  stats: DashboardStats;
  events: DashboardEvent[];
  topRoutes: RouteStats[];
  llmStats: LlmStats;
  trafficData: TrafficDataPoint[];
  llmUsageData: LlmUsageDataPoint[];
}

/**
 * Dashboard API 客户端类
 */
export class DashboardApiClient extends ApiClient {
  /**
   * 获取仪表盘数据
   * @param period 时间段 (day, week, month)
   * @returns 仪表盘数据
   */
  async getDashboardData(period: 'day' | 'week' | 'month' = 'day'): Promise<DashboardData> {
    return this.get<DashboardData>('/admin/dashboard/stats', { period });
  }

  /**
   * 获取流量数据
   * @param period 时间段 (day, week, month)
   * @returns 流量数据
   */
  async getTrafficData(period: 'day' | 'week' | 'month' = 'day'): Promise<TrafficDataPoint[]> {
    return this.get<{ data: TrafficDataPoint[] }>('/admin/dashboard/traffic', { period })
      .then(response => response.data);
  }

  /**
   * 获取 LLM 使用数据
   * @param period 时间段 (day, week, month)
   * @returns LLM 使用数据
   */
  async getLlmUsageData(period: 'day' | 'week' | 'month' = 'day'): Promise<LlmUsageDataPoint[]> {
    return this.get<{ data: LlmUsageDataPoint[] }>('/admin/dashboard/llm-usage', { period })
      .then(response => response.data);
  }

  /**
   * 获取最近事件
   * @param limit 事件数量限制
   * @returns 最近事件
   */
  async getRecentEvents(limit: number = 5): Promise<DashboardEvent[]> {
    return this.get<{ events: DashboardEvent[] }>('/admin/dashboard/events', { limit })
      .then(response => response.events);
  }
}

// 创建单例实例
export const dashboardApi = new DashboardApiClient();
