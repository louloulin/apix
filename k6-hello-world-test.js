import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// 自定义指标
const successfulRequests = new Counter('successful_requests');
const failedRequests = new Counter('failed_requests');
const requestDuration = new Trend('request_duration');
const requestRate = new Rate('request_rate');

export const options = {
  // 阶段式负载测试，逐步增加并发用户数
  stages: [
    { duration: '30s', target: 100 },   // 在30秒内增加到100个虚拟用户
    { duration: '1m', target: 500 },    // 在1分钟内增加到500个虚拟用户
    { duration: '1m', target: 1000 },   // 在1分钟内增加到1000个虚拟用户
    { duration: '1m', target: 2000 },   // 在1分钟内增加到2000个虚拟用户
    { duration: '1m', target: 5000 },   // 在1分钟内增加到5000个虚拟用户
    { duration: '1m', target: 5000 },   // 保持5000个虚拟用户1分钟
    { duration: '30s', target: 0 },     // 在30秒内减少到0个虚拟用户
  ],
  // 性能阈值
  thresholds: {
    http_req_duration: ['p(95)<50'], // 95%的请求应该在50ms内完成
    http_req_failed: ['rate<0.01'],  // 请求失败率应该小于1%
    'request_duration': ['p(95)<50'], // 自定义指标：95%的请求应该在50ms内完成
    'request_rate': ['rate>1000'],    // 自定义指标：请求速率应该大于1000请求/秒
  },
};

export default function () {
  // 发送请求到Hello World端点
  const startTime = new Date();
  const response = http.get('http://localhost:8080/hello');
  const endTime = new Date();
  const duration = endTime - startTime;
  
  // 记录请求持续时间
  requestDuration.add(duration);
  
  // 检查响应
  const checkResult = check(response, {
    'status is 200': (r) => r.status === 200,
    'response time < 50ms': (r) => r.timings.duration < 50,
    'content type is application/json': (r) => r.headers['Content-Type'] === 'application/json',
    'body contains Hello, World!': (r) => r.body.includes('Hello, World!'),
  });
  
  // 统计成功和失败的请求
  if (checkResult) {
    successfulRequests.add(1);
    requestRate.add(1);
  } else {
    failedRequests.add(1);
    requestRate.add(0);
    console.log(`Failed request to /hello: ${response.status} in ${response.timings.duration}ms`);
  }
  
  // 添加一些随机的思考时间，更接近真实用户行为
  sleep(Math.random() * 0.1); // 0-100ms的随机等待时间
}
