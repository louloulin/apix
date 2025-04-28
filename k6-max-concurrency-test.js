import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

// 自定义指标
const successfulRequests = new Counter('successful_requests');
const failedRequests = new Counter('failed_requests');

export const options = {
  // 阶段式负载测试，逐步增加并发用户数
  stages: [
    { duration: '30s', target: 100 },   // 在30秒内增加到100个虚拟用户
    { duration: '1m', target: 200 },    // 在1分钟内增加到200个虚拟用户
    { duration: '1m', target: 500 },    // 在1分钟内增加到500个虚拟用户
    { duration: '1m', target: 1000 },   // 在1分钟内增加到1000个虚拟用户
    { duration: '1m', target: 1000 },   // 保持1000个虚拟用户1分钟
    { duration: '30s', target: 0 },     // 在30秒内减少到0个虚拟用户
  ],
  // 性能阈值
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95%的请求应该在500ms内完成
    http_req_failed: ['rate<0.05'],   // 请求失败率应该小于5%
  },
};

export default function () {
  // 随机选择一个API端点进行测试
  const endpoints = [
    'http://localhost:8080/admin/config',
    'http://localhost:8080/admin/routes',
    'http://localhost:8080/admin/plugins',
    'http://localhost:8080/admin/services'
  ];
  
  const endpoint = endpoints[Math.floor(Math.random() * endpoints.length)];
  
  // 发送请求
  const response = http.get(endpoint);
  
  // 检查响应
  const checkResult = check(response, {
    'status is 200': (r) => r.status === 200,
    'response time < 500ms': (r) => r.timings.duration < 500,
  });
  
  // 统计成功和失败的请求
  if (checkResult) {
    successfulRequests.add(1);
  } else {
    failedRequests.add(1);
    console.log(`Failed request to ${endpoint}: ${response.status} in ${response.timings.duration}ms`);
  }
  
  // 添加一些随机的思考时间，更接近真实用户行为
  sleep(Math.random() * 0.5); // 0-500ms的随机等待时间
}
