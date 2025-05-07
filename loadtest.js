import http from 'k6/http';
import { sleep, check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// 自定义指标
const successCounter = new Counter('success_counter');
const failureCounter = new Counter('failure_counter');
const successRate = new Rate('success_rate');
const requestTrend = new Trend('request_trend');

// 测试配置
export const options = {
  // 预热阶段和负载阶段
  stages: [
    { duration: '10s', target: 100 },   // 预热：逐渐增加到100个虚拟用户
    { duration: '20s', target: 500 },    // 增加负载：在20秒内增加到500个虚拟用户
    { duration: '30s', target: 1000 },   // 增加负载：在30秒内增加到1000个虚拟用户
    { duration: '1m', target: 1000 },    // 稳定负载：保持1000个虚拟用户1分钟
    { duration: '10s', target: 0 },      // 冷却：逐渐减少到0个虚拟用户
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],   // 95%的请求应该在500ms内完成
    http_req_failed: ['rate<0.01'],     // 请求失败率应该小于1%
    'success_rate': ['rate>0.95'],      // 成功率应该大于95%
  },
};

// 测试端点
const API_BASE_URL = 'http://localhost:8080';

// 测试主函数
export default function() {
  // 测试高性能端点
  const pingRes = http.get(`${API_BASE_URL}/ping`);

  // 记录请求时间趋势
  requestTrend.add(pingRes.timings.duration);

  // 检查响应是否成功
  const pingCheck = check(pingRes, {
    'ping status is 200': (r) => r.status === 200,
    'ping response is pong': (r) => r.body === 'pong',
  });

  successRate.add(pingCheck);

  if (pingCheck) {
    successCounter.add(1);
  } else {
    failureCounter.add(1);
  }

  // 测试健康检查端点
  const healthRes = http.get(`${API_BASE_URL}/health`);

  // 检查响应是否成功
  const healthCheck = check(healthRes, {
    'health check status is 200': (r) => r.status === 200,
    'health check response contains status': (r) => r.body.includes('status'),
  });

  successRate.add(healthCheck);

  if (healthCheck) {
    successCounter.add(1);
  } else {
    failureCounter.add(1);
  }

  // 测试API Hello端点
  const helloRes = http.get(`${API_BASE_URL}/hello`);

  // 检查API响应
  const helloCheck = check(helloRes, {
    'hello status is 200': (r) => r.status === 200,
    'hello response contains message': (r) => r.body.includes('message'),
  });

  successRate.add(helloCheck);

  if (helloCheck) {
    successCounter.add(1);
  } else {
    failureCounter.add(1);
  }

  // 测试API端点
  const apiRes = http.get(`${API_BASE_URL}/api/hello`);

  // 检查API响应
  const apiCheck = check(apiRes, {
    'API status is 200': (r) => r.status === 200,
    'API response contains message': (r) => r.body.includes('message'),
  });

  successRate.add(apiCheck);

  if (apiCheck) {
    successCounter.add(1);
  } else {
    failureCounter.add(1);
  }

  // 在请求之间添加一些随机延迟，模拟真实用户行为
  sleep(Math.random() * 0.5); // 减少延迟以增加负载
}
