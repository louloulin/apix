import http from 'k6/http';
import { check, sleep } from 'k6';

// 测试配置
export const options = {
  scenarios: {
    // 常量负载场景
    constant_load: {
      executor: 'constant-arrival-rate',
      rate: 1000,              // 每秒1000个请求
      timeUnit: '1s',          // 时间单位
      duration: '30s',         // 持续30秒
      preAllocatedVUs: 100,    // 预分配的虚拟用户数
      maxVUs: 200,             // 最大虚拟用户数
    },
    // 阶梯负载场景
    ramp_load: {
      executor: 'ramping-arrival-rate',
      startRate: 50,           // 开始每秒50个请求
      timeUnit: '1s',          // 时间单位
      preAllocatedVUs: 50,     // 预分配的虚拟用户数
      maxVUs: 500,             // 最大虚拟用户数
      stages: [
        { duration: '10s', target: 200 },   // 在10秒内增加到每秒200个请求
        { duration: '30s', target: 200 },   // 保持每秒200个请求30秒
        { duration: '10s', target: 500 },   // 在10秒内增加到每秒500个请求
        { duration: '30s', target: 500 },   // 保持每秒500个请求30秒
        { duration: '10s', target: 0 },     // 在10秒内减少到每秒0个请求
      ],
    },
    // 突发负载场景
    spike_load: {
      executor: 'ramping-arrival-rate',
      startRate: 10,           // 开始每秒10个请求
      timeUnit: '1s',          // 时间单位
      preAllocatedVUs: 10,     // 预分配的虚拟用户数
      maxVUs: 1000,            // 最大虚拟用户数
      stages: [
        { duration: '10s', target: 10 },    // 保持每秒10个请求10秒
        { duration: '5s', target: 1000 },   // 在5秒内突增到每秒1000个请求
        { duration: '10s', target: 1000 },  // 保持每秒1000个请求10秒
        { duration: '5s', target: 10 },     // 在5秒内减少到每秒10个请求
        { duration: '10s', target: 10 },    // 保持每秒10个请求10秒
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95%的请求响应时间小于500ms
    http_req_failed: ['rate<0.01'],   // 请求失败率小于1%
  },
};

// 测试URL
const BASE_URL = 'http://localhost:8888';

// 测试数据
const TEST_DATA = {
  small: { value: 'test', timestamp: Date.now() },
  medium: { value: 'test', data: Array(100).fill('medium data'), timestamp: Date.now() },
  large: { value: 'test', data: Array(1000).fill('large data'), timestamp: Date.now() },
};

// 默认请求
export default function() {
  // GET请求
  const getResponse = http.get(`${BASE_URL}/api/echo?param=value&timestamp=${Date.now()}`);
  check(getResponse, {
    'GET status is 200': (r) => r.status === 200,
    'GET response has param': (r) => r.json().param === 'value',
  });
  
  // POST请求
  const postResponse = http.post(
    `${BASE_URL}/api/process`,
    JSON.stringify(TEST_DATA.small),
    { headers: { 'Content-Type': 'application/json' } }
  );
  check(postResponse, {
    'POST status is 200': (r) => r.status === 200,
    'POST response is success': (r) => r.json().success === true,
  });
  
  // 随机休眠一段时间（0-1秒）
  sleep(Math.random());
}

// 小数据测试
export function smallData() {
  const response = http.post(
    `${BASE_URL}/api/process`,
    JSON.stringify(TEST_DATA.small),
    { headers: { 'Content-Type': 'application/json' } }
  );
  check(response, {
    'Small data status is 200': (r) => r.status === 200,
    'Small data response is success': (r) => r.json().success === true,
  });
}

// 中等数据测试
export function mediumData() {
  const response = http.post(
    `${BASE_URL}/api/process`,
    JSON.stringify(TEST_DATA.medium),
    { headers: { 'Content-Type': 'application/json' } }
  );
  check(response, {
    'Medium data status is 200': (r) => r.status === 200,
    'Medium data response is success': (r) => r.json().success === true,
  });
}

// 大数据测试
export function largeData() {
  const response = http.post(
    `${BASE_URL}/api/process`,
    JSON.stringify(TEST_DATA.large),
    { headers: { 'Content-Type': 'application/json' } }
  );
  check(response, {
    'Large data status is 200': (r) => r.status === 200,
    'Large data response is success': (r) => r.json().success === true,
  });
}

// 错误测试
export function errorTest() {
  const response = http.post(
    `${BASE_URL}/api/process`,
    'invalid json',
    { headers: { 'Content-Type': 'application/json' } }
  );
  check(response, {
    'Error test status is 400': (r) => r.status === 400,
    'Error test response is not success': (r) => r.json().success === false,
  });
}

// 批量请求测试
export function batchRequests() {
  const requests = [];
  
  // 添加10个GET请求
  for (let i = 0; i < 10; i++) {
    requests.push({
      method: 'GET',
      url: `${BASE_URL}/api/echo?param=value&index=${i}&timestamp=${Date.now()}`,
    });
  }
  
  // 添加5个POST请求
  for (let i = 0; i < 5; i++) {
    requests.push({
      method: 'POST',
      url: `${BASE_URL}/api/process`,
      body: JSON.stringify({ ...TEST_DATA.small, index: i }),
      params: { headers: { 'Content-Type': 'application/json' } },
    });
  }
  
  // 批量发送请求
  const responses = http.batch(requests);
  
  // 检查所有响应
  for (const response of responses) {
    check(response, {
      'Batch request status is 200': (r) => r.status === 200,
    });
  }
}
