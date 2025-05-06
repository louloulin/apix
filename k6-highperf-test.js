import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// 自定义指标
const successRate = new Rate('success_rate');
const errorRate = new Rate('error_rate');
const responseTime = new Trend('response_time');
const requestsPerSecond = new Counter('requests_per_second');

// 测试配置 - 针对高性能服务器
export const options = {
  // 基本配置
  vus: 1000,              // 并发用户数
  duration: '30s',        // 测试持续时间
  
  // 系统资源配置
  discardResponseBodies: true,  // 丢弃响应体以节省内存
  noConnectionReuse: false,     // 重用连接以最大化并发
  
  // HTTP配置
  http: {
    timeout: '10s',       // 请求超时时间
  },
  
  // 阈值配置
  thresholds: {
    http_req_duration: ['p(95)<50'], // 95% 的请求响应时间小于 50ms
    http_req_failed: ['rate<0.01'],  // 请求失败率小于 1%
  },
};

// 测试函数 - 使用高性能服务器端点
export default function() {
  // 发送请求到高性能服务器
  const response = http.get('http://localhost:8081/');
  
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
  sleep(0.001);  // 1ms
}
