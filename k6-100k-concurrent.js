import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// 自定义指标
const successRate = new Rate('success_rate');
const errorRate = new Rate('error_rate');
const responseTime = new Trend('response_time');
const requestsPerSecond = new Counter('requests_per_second');
const activeConnections = new Counter('active_connections');

// 测试配置 - 专为10万并发优化
export const options = {
  // 系统资源配置
  discardResponseBodies: true,  // 丢弃响应体以节省内存
  noConnectionReuse: false,     // 重用连接以最大化并发
  
  // 阈值配置 - 针对高并发场景放宽要求
  thresholds: {
    http_req_duration: ['p(95)<1000'], // 95% 的请求响应时间小于 1000ms
    http_req_failed: ['rate<0.10'],    // 请求失败率小于 10%
  },

  // 使用场景配置 - 逐步增加到10万并发
  scenarios: {
    ramp_to_100k: {
      executor: 'ramping-vus',
      startVUs: 100,
      stages: [
        { duration: '10s', target: 1000 },    // 10秒内增加到 1000 并发
        { duration: '10s', target: 1000 },    // 保持 1000 并发 10 秒
        { duration: '10s', target: 5000 },    // 10秒内增加到 5000 并发
        { duration: '10s', target: 5000 },    // 保持 5000 并发 10 秒
        { duration: '10s', target: 10000 },   // 10秒内增加到 10000 并发
        { duration: '10s', target: 10000 },   // 保持 10000 并发 10 秒
        { duration: '10s', target: 20000 },   // 10秒内增加到 20000 并发
        { duration: '10s', target: 20000 },   // 保持 20000 并发 10 秒
        { duration: '10s', target: 50000 },   // 10秒内增加到 50000 并发
        { duration: '10s', target: 50000 },   // 保持 50000 并发 10 秒
        { duration: '10s', target: 100000 },  // 10秒内增加到 100000 并发
        { duration: '20s', target: 100000 },  // 保持 100000 并发 20 秒
        { duration: '10s', target: 0 },       // 10秒内减少到 0 并发
      ],
      gracefulRampDown: '30s',
    },
  },
};

// 测试函数 - 极简化以支持高并发
export default function() {
  // 记录活动连接数
  activeConnections.add(1);

  try {
    // 发送请求到正确的端点 - 使用最简单的请求
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
  } catch (e) {
    // 记录错误
    errorRate.add(1);
    console.log(`Error: ${e.message}`);
  } finally {
    // 减少活动连接数
    activeConnections.add(-1);
  }

  // 动态休眠时间 - 根据并发用户数调整
  const vuCount = __VU;
  let sleepTime = 0.01;  // 默认 10ms

  if (vuCount > 75000) {
    sleepTime = 0.05;  // 7.5-10 万并发使用 50ms 休眠
  } else if (vuCount > 50000) {
    sleepTime = 0.03;  // 5-7.5 万并发使用 30ms 休眠
  } else if (vuCount > 25000) {
    sleepTime = 0.02;  // 2.5-5 万并发使用 20ms 休眠
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
    './k6-100k-results.json': JSON.stringify(data),
  };
}
