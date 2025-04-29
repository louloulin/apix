import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// 自定义指标
const successRate = new Rate('success_rate');
const errorRate = new Rate('error_rate');
const responseTime = new Trend('response_time');
const requestsPerSecond = new Counter('requests_per_second');

// 测试配置 - 简单连接测试
export const options = {
  // 系统资源配置
  discardResponseBodies: true,  // 丢弃响应体以节省内存
  noConnectionReuse: false,     // 重用连接以最大化并发
  
  // 使用场景配置 - 简单的负载曲线
  scenarios: {
    simple_test: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 100 },     // 10秒内增加到 100 并发
        { duration: '20s', target: 1000 },    // 20秒内增加到 1000 并发
        { duration: '30s', target: 5000 },    // 30秒内增加到 5000 并发
        { duration: '30s', target: 5000 },    // 维持 5000 并发 30 秒
        { duration: '10s', target: 0 },       // 10秒内减少到 0 并发
      ],
      gracefulRampDown: '10s',
    },
  },
  
  // 阈值配置
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% 的请求响应时间小于 500ms
    http_req_failed: ['rate<0.01'],   // 请求失败率小于 1%
    'success_rate': ['rate>0.99'],    // 成功率大于 99%
    'error_rate': ['rate<0.01'],      // 错误率小于 1%
  },
};

// 测试函数
export default function() {
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
  
  // 休眠时间
  sleep(0.1);  // 100ms
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
  
  return {
    'stdout': JSON.stringify(data),
    './k6-simple-connection-test-results.json': JSON.stringify(data),
  };
}
