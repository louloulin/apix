import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Counter, Trend } from 'k6/metrics';

// 自定义指标
const successRate = new Rate('success_rate');
const errorRate = new Rate('error_rate');
const responseTime = new Trend('response_time');
const requestsPerSecond = new Counter('requests_per_second');

// 测试配置
export const options = {
  // 阶段式负载
  stages: [
    { duration: '30s', target: 200 },   // 30秒内增加到 200 并发用户
    { duration: '1m', target: 200 },    // 维持 200 并发用户 1 分钟
    { duration: '30s', target: 500 },   // 30秒内增加到 500 并发用户
    { duration: '1m', target: 500 },    // 维持 500 并发用户 1 分钟
    { duration: '30s', target: 1000 },  // 30秒内增加到 1000 并发用户
    { duration: '1m', target: 1000 },   // 维持 1000 并发用户 1 分钟
    { duration: '30s', target: 0 },     // 30秒内减少到 0 并发用户
  ],
  
  // 阈值设置
  thresholds: {
    http_req_duration: ['p(95)<1000'], // 95% 的请求响应时间小于 1000ms
    'success_rate': ['rate>0.9'],      // 成功率大于 90%
    'error_rate': ['rate<0.1'],        // 错误率小于 10%
  },
};

// 测试函数
export default function() {
  // 生成随机服务 ID，模拟不同服务
  const serviceId = `service-${Math.floor(Math.random() * 10)}`;
  
  // 发送请求
  const startTime = new Date().getTime();
  const response = http.get(`http://localhost:8080/api/hello?serviceId=${serviceId}`, {
    headers: {
      'X-Service-ID': serviceId,
    },
  });
  const endTime = new Date().getTime();
  
  // 计算响应时间
  const duration = endTime - startTime;
  responseTime.add(duration);
  
  // 检查响应
  const success = response.status >= 200 && response.status < 300;
  const error = !success;
  
  // 更新指标
  successRate.add(success);
  errorRate.add(error);
  requestsPerSecond.add(1);
  
  // 检查响应
  check(response, {
    'status is 200 or 429': (r) => r.status === 200 || r.status === 429,
    'response is valid': (r) => r.body.length > 0,
  });
  
  // 短暂休眠，模拟用户行为
  sleep(0.1);
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
    './k6-concurrency-results.json': JSON.stringify(data),
  };
}
