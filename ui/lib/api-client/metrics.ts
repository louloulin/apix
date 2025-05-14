/**
 * 系统指标 API 客户端
 */
import { ApiClient } from "./base";

/**
 * 系统指标接口
 */
export interface SystemMetrics {
  timestamp: number;
  uptime: number;
  requestCount: number;
  activeConnections: number;
  requestsPerSecond: number;
  averageResponseTime: number;
  errorRate: number;
  cpu: CpuMetrics;
  memory: MemoryMetrics;
  threads: ThreadMetrics;
  jvm: JvmMetrics;
  os: OsMetrics;
}

/**
 * CPU 指标接口
 */
export interface CpuMetrics {
  systemCpuLoad: number;
  processCpuLoad: number;
  availableProcessors: number;
  systemLoadAverage: number;
  processCpuTime: number;
  cores: Array<{
    coreId: number;
    usage: number;
  }>;
}

/**
 * 内存指标接口
 */
export interface MemoryMetrics {
  heapMemoryUsed: number;
  heapMemoryMax: number;
  heapMemoryCommitted: number;
  nonHeapMemoryUsed: number;
  nonHeapMemoryCommitted: number;
  systemMemoryTotal: number;
  systemMemoryFree: number;
  systemMemoryUsed: number;
  memoryPools: Array<{
    name: string;
    used: number;
    max: number;
    committed: number;
  }>;
}

/**
 * 线程指标接口
 */
export interface ThreadMetrics {
  threadCount: number;
  daemonThreadCount: number;
  peakThreadCount: number;
  totalStartedThreadCount: number;
  deadlockedThreads: number;
  threadStates: Array<{
    state: string;
    count: number;
  }>;
}

/**
 * JVM 指标接口
 */
export interface JvmMetrics {
  jvmName: string;
  jvmVersion: string;
  jvmVendor: string;
  startTime: number;
  uptime: number;
  gcCollectors: Array<{
    name: string;
    collectionCount: number;
    collectionTime: number;
  }>;
  classLoading: {
    loadedClassCount: number;
    totalLoadedClassCount: number;
    unloadedClassCount: number;
  };
}

/**
 * 操作系统指标接口
 */
export interface OsMetrics {
  name: string;
  version: string;
  arch: string;
  availableProcessors: number;
  systemLoadAverage: number;
  committedVirtualMemory: number;
  totalSwapSpace: number;
  freeSwapSpace: number;
  totalPhysicalMemory: number;
  freePhysicalMemory: number;
  fileDescriptors: {
    open: number;
    max: number;
  };
}

/**
 * 健康检查接口
 */
export interface HealthCheck {
  status: 'UP' | 'DOWN' | 'UNKNOWN';
  timestamp: number;
  components: Array<{
    name: string;
    status: 'UP' | 'DOWN' | 'UNKNOWN';
    details?: Record<string, any>;
  }>;
}

/**
 * 系统指标查询参数接口
 */
export interface MetricsQueryParams {
  from?: number;
  to?: number;
  interval?: string;
}

/**
 * 系统指标 API 客户端类
 */
export class MetricsApiClient extends ApiClient {
  /**
   * 获取所有系统指标
   * @param params 查询参数
   * @returns 系统指标
   */
  async getMetrics(params?: MetricsQueryParams) {
    return this.get<SystemMetrics>('/admin/metrics', params);
  }

  /**
   * 获取 CPU 指标
   * @param params 查询参数
   * @returns CPU 指标
   */
  async getCpuMetrics(params?: MetricsQueryParams) {
    return this.get<CpuMetrics>('/admin/metrics/cpu', params);
  }

  /**
   * 获取内存指标
   * @param params 查询参数
   * @returns 内存指标
   */
  async getMemoryMetrics(params?: MetricsQueryParams) {
    return this.get<MemoryMetrics>('/admin/metrics/memory', params);
  }

  /**
   * 获取线程指标
   * @param params 查询参数
   * @returns 线程指标
   */
  async getThreadMetrics(params?: MetricsQueryParams) {
    return this.get<ThreadMetrics>('/admin/metrics/threads', params);
  }

  /**
   * 获取 JVM 指标
   * @param params 查询参数
   * @returns JVM 指标
   */
  async getJvmMetrics(params?: MetricsQueryParams) {
    return this.get<JvmMetrics>('/admin/metrics/jvm', params);
  }

  /**
   * 获取操作系统指标
   * @param params 查询参数
   * @returns 操作系统指标
   */
  async getOsMetrics(params?: MetricsQueryParams) {
    return this.get<OsMetrics>('/admin/metrics/os', params);
  }

  /**
   * 获取健康检查
   * @returns 健康检查
   */
  async getHealth() {
    return this.get<HealthCheck>('/admin/health');
  }

  /**
   * 获取历史指标
   * @param params 查询参数
   * @returns 历史指标
   */
  async getHistoricalMetrics(params: MetricsQueryParams) {
    return this.get<Array<SystemMetrics>>('/admin/metrics/history', params);
  }
}

// 创建单例实例
export const metricsApi = new MetricsApiClient();
