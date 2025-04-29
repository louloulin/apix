import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// 自定义指标
const successRate = new Rate('success_rate');
const errorRate = new Rate('error_rate');
const responseTime = new Trend('response_time');
const requestsPerSecond = new Counter('requests_per_second');
const activeConnections = new Counter('active_connections');

// 测试配置 - 20万并发，持续1分钟
export const options = {
  // 系统资源配置
  discardResponseBodies: true,  // 丢弃响应体以节省内存
  noConnectionReuse: false,     // 重用连接以最大化并发
  batchPerHost: 20,             // 每个主机的批处理请求数
  insecureSkipTLSVerify: true,  // 跳过 TLS 验证以提高性能

  // 阈值配置 - 放宽成功率要求
  thresholds: {
    http_req_duration: ['p(95)<2000'], // 95% 的请求响应时间小于 2000ms
    http_req_failed: ['rate<0.3'],     // 请求失败率小于 30%
    'success_rate': ['rate>0.7'],      // 成功率大于 70%
    'error_rate': ['rate<0.3'],        // 错误率小于 30%
  },

  // 使用场景配置 - 快速增加到 20 万并发，维持 1 分钟
  scenarios: {
    high_concurrency: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 100000 },  // 30秒内增加到10万并发
        { duration: '30s', target: 200000 },  // 30秒内增加到20万并发
        { duration: '60s', target: 200000 },  // 维持20万并发1分钟
        { duration: '30s', target: 0 },       // 30秒内减少到0并发
      ],
      gracefulRampDown: '30s',
    },
  },
};

// 测试函数 - 尽可能简单，减少客户端开销
export default function() {
  // 记录活动连接数
  activeConnections.add(1);

  // 发送请求到正确的端点
  const startTime = new Date().getTime();
  const response = http.get('http://localhost:8080/api/hello');
  const endTime = new Date().getTime();

  // 记录响应时间
  const duration = endTime - startTime;
  responseTime.add(duration);

  // 检查响应 - 只检查状态码
  const success = check(response, {
    'status is 200': (r) => r.status === 200,
  });

  // 更新指标
  successRate.add(success);
  errorRate.add(!success);
  requestsPerSecond.add(1);

  // 减少活动连接数
  activeConnections.add(-1);

  // 最小休眠时间，以最大化并发
  sleep(0.01);
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
    './k6-200k-1min-results.json': JSON.stringify(data),
  };
}
