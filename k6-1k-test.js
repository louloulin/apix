import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// 自定义指标
const successRate = new Rate('success_rate');
const errorRate = new Rate('error_rate');
const responseTime = new Trend('response_time');
const requestsPerSecond = new Counter('requests_per_second');

// 测试配置 - 1000并发用户
export const options = {
  // 基本配置
  vus: 1000,              // 并发用户数
  duration: '30s',        // 测试持续时间
  
  // 系统资源配置
  discardResponseBodies: true,  // 丢弃响应体以节省内存
  noConnectionReuse: false,     // 重用连接以最大化并发
  
  // 阈值配置
  thresholds: {
    http_req_duration: ['p(95)<100'], // 95% 的请求响应时间小于 100ms
    http_req_failed: ['rate<0.01'],   // 请求失败率小于 1%
  },
};

// 测试函数 - 最简单的请求
export default function() {
  // 发送请求到正确的端点
  const response = http.get('http://localhost:8080/api/hello');
  
  // 记录响应时间
  responseTime.add(response.timings.duration);
  
  // 检查响应 - 只检查状态码
  const success = check(response, {
    'status is 200': (r) => r.status === 200,
  });
  
  // 更新指标
  successRate.add(success);
  errorRate.add(!success);
  requestsPerSecond.add(1);
  
  // 最小休眠时间
  sleep(0.01);  // 10ms
}
