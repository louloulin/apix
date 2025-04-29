import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// 自定义指标
const successRate = new Rate('success_rate');
const errorRate = new Rate('error_rate');
const responseTime = new Trend('response_time');
const requestsPerSecond = new Counter('requests_per_second');
const activeConnections = new Counter('active_connections');

// 测试配置 - 优化版 20 万并发
export const options = {
  // 系统资源配置
  discardResponseBodies: true,  // 丢弃响应体以节省内存
  noConnectionReuse: false,     // 重用连接以最大化并发
  batchPerHost: 20,             // 每个主机的批处理请求数
  insecureSkipTLSVerify: true,  // 跳过 TLS 验证以提高性能

  // 阈值配置 - 更宽松的阈值以适应高并发
  thresholds: {
    http_req_duration: ['p(95)<5000'], // 95% 的请求响应时间小于 5000ms
    http_req_failed: ['rate<0.1'],     // 请求失败率小于 10%
    'success_rate': ['rate>0.9'],      // 成功率大于 90%
    'error_rate': ['rate<0.1'],        // 错误率小于 10%
  },

  // 使用场景配置替代 stages
  scenarios: {
    high_concurrency: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '20s', target: 10000 },   // 20秒内增加到 1 万并发
        { duration: '20s', target: 50000 },   // 20秒内增加到 5 万并发
        { duration: '30s', target: 100000 },  // 30秒内增加到 10 万并发
        { duration: '30s', target: 150000 },  // 30秒内增加到 15 万并发
        { duration: '30s', target: 200000 },  // 30秒内增加到 20 万并发
        { duration: '60s', target: 200000 },  // 维持 20 万并发 60 秒
        { duration: '30s', target: 100000 },  // 30秒内减少到 10 万并发
        { duration: '20s', target: 0 },       // 20秒内减少到 0 并发
      ],
      gracefulRampDown: '30s',
    },
  },
};

// 测试函数 - 优化版
export default function() {
  // 记录活动连接数
  activeConnections.add(1);

  // 发送请求到正确的端点
  const startTime = new Date().getTime();
  const response = http.get('http://localhost:8080/api/hello', {
    headers: {
      'Accept': 'application/json',
      'User-Agent': 'k6-load-test/1.0'
    },
    tags: { name: 'ApiHelloRequest' }
  });
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
  // 使用动态休眠时间，根据 VU 数量调整
  const vuCount = __VU;
  let sleepTime = 0.01;  // 基础休眠时间 10ms

  if (vuCount > 100000) {
    sleepTime = 0.005;  // 5ms
  } else if (vuCount > 50000) {
    sleepTime = 0.008;  // 8ms
  }

  sleep(sleepTime);
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
    './k6-200k-optimized-results.json': JSON.stringify(data),
  };
}
