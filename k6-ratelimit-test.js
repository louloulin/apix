import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Counter, Trend } from 'k6/metrics';

// 自定义指标
const successRate = new Rate('success_rate');
const limitedRate = new Rate('limited_rate');
const responseTime = new Trend('response_time');
const requestsPerSecond = new Counter('requests_per_second');

// 测试配置
export const options = {
  // 阶段式负载
  stages: [
    { duration: '30s', target: 100 },   // 30秒内增加到 100 并发用户
    { duration: '1m', target: 100 },    // 维持 100 并发用户 1 分钟
    { duration: '30s', target: 500 },   // 30秒内增加到 500 并发用户
    { duration: '1m', target: 500 },    // 维持 500 并发用户 1 分钟
    { duration: '30s', target: 1000 },  // 30秒内增加到 1000 并发用户
    { duration: '1m', target: 1000 },   // 维持 1000 并发用户 1 分钟
    { duration: '30s', target: 0 },     // 30秒内减少到 0 并发用户
  ],
  
  // 阈值设置
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% 的请求响应时间小于 500ms
    'success_rate': ['rate>0.7'],     // 成功率大于 70%
    'limited_rate': ['rate<0.3'],     // 限流率小于 30%
  },
};

// 测试函数
export default function() {
  // 生成随机用户 ID，模拟不同用户
  const userId = Math.floor(Math.random() * 1000);
  
  // 发送请求
  const startTime = new Date().getTime();
  const response = http.get(`http://localhost:8080/api/hello?userId=${userId}`, {
    headers: {
      'X-API-Key': `user-${userId}`,
    },
  });
  const endTime = new Date().getTime();
  
  // 计算响应时间
  const duration = endTime - startTime;
  responseTime.add(duration);
  
  // 检查响应
  const success = response.status === 200;
  const limited = response.status === 429;
  
  // 更新指标
  successRate.add(success);
  limitedRate.add(limited);
  requestsPerSecond.add(1);
  
  // 检查响应头
  check(response, {
    'success or rate limited': (r) => r.status === 200 || r.status === 429,
    'has rate limit headers': (r) => r.headers['X-RateLimit-Limit'] !== undefined,
  });
  
  // 根据响应状态调整休眠时间
  if (limited) {
    // 如果被限流，等待更长时间
    sleep(1);
  } else {
    // 正常请求，短暂休眠
    sleep(0.1);
  }
}

// 测试结束后的汇总函数
export function handleSummary(data) {
  console.log('Test completed');
  console.log(`Total requests: ${data.metrics.http_reqs.values.count}`);
  console.log(`Request rate: ${data.metrics.http_reqs.values.rate.toFixed(2)} req/s`);
  console.log(`Average response time: ${data.metrics.http_req_duration.values.avg.toFixed(2)} ms`);
  console.log(`95th percentile response time: ${data.metrics.http_req_duration.values['p(95)'].toFixed(2)} ms`);
  console.log(`Success rate: ${(data.metrics.success_rate.values.rate * 100).toFixed(2)}%`);
  console.log(`Limited rate: ${(data.metrics.limited_rate.values.rate * 100).toFixed(2)}%`);
  
  return {
    'stdout': JSON.stringify(data),
    './k6-ratelimit-results.json': JSON.stringify(data),
  };
}
