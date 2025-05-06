import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// 自定义指标
const successRate = new Rate('success_rate');
const errorRate = new Rate('error_rate');
const requestDuration = new Trend('request_duration');
const requestsPerSecond = new Counter('requests_per_second');

// 测试配置
export const options = {
  vus: 10,               // 虚拟用户数
  duration: '30s',       // 测试持续时间
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95%的请求响应时间小于500ms
    http_req_failed: ['rate<0.01'],   // 请求失败率小于1%
    'success_rate': ['rate>0.95'],    // 成功率大于95%
  },
};

// 测试URL
const BASE_URL = 'http://localhost:8080';

// 测试数据
const TEST_DATA = {
  small: { value: 'test', timestamp: Date.now() },
  medium: { value: 'test', data: Array(100).fill('medium data'), timestamp: Date.now() },
  large: { value: 'test', data: Array(1000).fill('large data'), timestamp: Date.now() },
};

// 测试EventBus
export default function() {
  // 记录开始时间
  const startTime = new Date();

  // 发送POST请求，模拟EventBus消息
  const response = http.post(
    `${BASE_URL}/api/eventbus`,
    JSON.stringify({
      address: 'test.eventbus',
      body: TEST_DATA.small
    }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  // 记录请求持续时间
  const duration = new Date() - startTime;
  requestDuration.add(duration);
  requestsPerSecond.add(1);

  // 检查响应
  const success = check(response, {
    'status is 200': (r) => r.status === 200,
    'response is success': (r) => JSON.parse(r.body).success === true,
  });

  // 记录成功率和错误率
  successRate.add(success);
  errorRate.add(!success);

  // 随机休眠一段时间（0-1秒）
  sleep(Math.random());
}

// 测试大数据量
export function testLargeData() {
  // 记录开始时间
  const startTime = new Date();

  // 发送POST请求，模拟大数据量
  const response = http.post(
    `${BASE_URL}/api/data`,
    JSON.stringify(TEST_DATA.large),
    { headers: { 'Content-Type': 'application/json' } }
  );

  // 记录请求持续时间
  const duration = new Date() - startTime;
  requestDuration.add(duration);
  requestsPerSecond.add(1);

  // 检查响应
  const success = check(response, {
    'status is 200': (r) => r.status === 200,
    'response is success': (r) => JSON.parse(r.body).success === true,
  });

  // 记录成功率和错误率
  successRate.add(success);
  errorRate.add(!success);

  // 随机休眠一段时间（0-1秒）
  sleep(Math.random());
}

// 测试并发请求
export function testConcurrentRequests() {
  // 记录开始时间
  const startTime = new Date();

  // 创建多个请求
  const requests = [];
  for (let i = 0; i < 10; i++) {
    requests.push({
      method: 'GET',
      url: `${BASE_URL}/api/hello?index=${i}`,
    });
  }

  // 批量发送请求
  const responses = http.batch(requests);

  // 记录请求持续时间
  const duration = new Date() - startTime;
  requestDuration.add(duration);
  requestsPerSecond.add(requests.length);

  // 检查所有响应
  let allSuccess = true;
  for (const response of responses) {
    if (response.status !== 200) {
      allSuccess = false;
      break;
    }
  }

  // 记录成功率和错误率
  successRate.add(allSuccess);
  errorRate.add(!allSuccess);

  // 随机休眠一段时间（0-1秒）
  sleep(Math.random());
}
