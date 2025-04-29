import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// 自定义指标
const successRate = new Rate('success_rate');
const errorRate = new Rate('error_rate');
const responseTime = new Trend('response_time');
const requestsPerSecond = new Counter('requests_per_second');
const activeConnections = new Counter('active_connections');

// 测试配置 - 中等负载模式
export const options = {
  // 阶段式负载配置 - 适中的负载模式
  stages: [
    { duration: '10s', target: 100 },    // 逐步增加到 100 个并发用户
    { duration: '20s', target: 500 },    // 增加到 500 个并发用户
    { duration: '30s', target: 1000 },   // 增加到 1000 个并发用户
    { duration: '30s', target: 2000 },   // 增加到 2000 个并发用户
    { duration: '30s', target: 1000 },   // 减少到 1000 个并发用户
    { duration: '10s', target: 0 },      // 逐步减少到 0 个并发用户
  ],

  // 系统资源配置
  discardResponseBodies: true,  // 丢弃响应体以节省内存
  noConnectionReuse: false,     // 重用连接以最大化并发

  // 阈值配置
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% 的请求响应时间小于 500ms
    http_req_failed: ['rate<0.05'],   // 请求失败率小于 5%
    'success_rate': ['rate>0.95'],    // 成功率大于 95%
    'error_rate': ['rate<0.05'],      // 错误率小于 5%
  },
};

// 测试函数
export default function() {
  // 记录活动连接数
  activeConnections.add(1);

  // 发送请求
  const startTime = new Date().getTime();
  const response = http.get('http://localhost:8080/hello');
  const endTime = new Date().getTime();

  // 记录响应时间
  const duration = endTime - startTime;
  responseTime.add(duration);

  // 检查响应
  const success = check(response, {
    'status is 200': (r) => r.status === 200,
  });

  // 只检查状态码，不检查响应体和内容类型

  // 更新指标
  successRate.add(success);
  errorRate.add(!success);
  requestsPerSecond.add(1);

  // 减少活动连接数
  activeConnections.add(-1);

  // 随机休眠 0-0.2 秒
  sleep(Math.random() * 0.2);
}

// 测试结束后的汇总函数
export function handleSummary(data) {
  console.log('Test completed');
  console.log(`Total requests: ${data.metrics.http_reqs.values.count}`);
  console.log(`Request rate: ${data.metrics.http_reqs.values.rate.toFixed(2)} req/s`);
  console.log(`Average response time: ${data.metrics.http_req_duration.values.avg.toFixed(2)} ms`);
  console.log(`95th percentile response time: ${data.metrics.http_req_duration.values['p(95)'].toFixed(2)} ms`);
  console.log(`Success rate: ${(data.metrics.success_rate.values.rate * 100).toFixed(2)}%`);
  console.log(`Error rate: ${(data.metrics.error_rate.values.rate * 100).toFixed(2)}%`);
  console.log(`Max active connections: ${data.metrics.active_connections.values.max}`);

  return {
    'stdout': JSON.stringify(data),
    './k6-moderate-load-results.json': JSON.stringify(data),
  };
}
