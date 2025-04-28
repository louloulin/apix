import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// 自定义指标
const successfulRequests = new Counter('successful_requests');
const failedRequests = new Counter('failed_requests');
const requestDuration = new Trend('request_duration');
const requestRate = new Rate('request_rate');

export const options = {
  // 阶段式负载测试，逐步增加并发用户数到 10 万级别
  stages: [
    { duration: '30s', target: 1000 },    // 在30秒内增加到1000个虚拟用户
    { duration: '1m', target: 5000 },     // 在1分钟内增加到5000个虚拟用户
    { duration: '1m', target: 10000 },    // 在1分钟内增加到10000个虚拟用户
    { duration: '1m', target: 20000 },    // 在1分钟内增加到20000个虚拟用户
    { duration: '1m', target: 50000 },    // 在1分钟内增加到50000个虚拟用户
    { duration: '1m', target: 100000 },   // 在1分钟内增加到100000个虚拟用户
    { duration: '1m', target: 100000 },   // 保持100000个虚拟用户1分钟
    { duration: '30s', target: 0 },       // 在30秒内减少到0个虚拟用户
  ],
  // 性能阈值
  thresholds: {
    http_req_duration: ['p(95)<100'], // 95%的请求应该在100ms内完成
    http_req_failed: ['rate<0.01'],   // 请求失败率应该小于1%
    'request_duration': ['p(95)<100'], // 自定义指标：95%的请求应该在100ms内完成
    'request_rate': ['rate>10000'],    // 自定义指标：请求速率应该大于10000请求/秒
  },
  // 禁用默认的请求超时，使用我们自己的超时设置
  timeout: '10s',
  // 禁用TLS验证以提高性能
  insecureSkipTLSVerify: true,
  // 禁用连接复用以模拟更真实的高并发场景
  noConnectionReuse: false,
  // 禁用请求压缩以减少CPU开销
  noVUConnectionReuse: false,
  // 增加用户代理标识
  userAgent: 'K6-Ultra-Concurrency-Test/1.0',
  // 批处理请求以提高性能
  batch: 50,
  // 增加最大重定向次数
  maxRedirects: 0,
  // 禁用响应超时
  discardResponseBodies: true,
  // 设置DNS解析超时
  dns: {
    ttl: '1m',
    select: 'first',
    policy: 'preferIPv4',
  },
};

export default function () {
  // 使用超轻量级的 /ping 端点进行测试，以最大化并发性能
  const startTime = new Date();
  const response = http.get('http://localhost:8080/ping');
  const endTime = new Date();
  const duration = endTime - startTime;
  
  // 记录请求持续时间
  requestDuration.add(duration);
  
  // 检查响应
  const checkResult = check(response, {
    'status is 200': (r) => r.status === 200,
    'response time < 100ms': (r) => r.timings.duration < 100,
    'body contains pong': (r) => r.body.includes('pong'),
  });
  
  // 统计成功和失败的请求
  if (checkResult) {
    successfulRequests.add(1);
    requestRate.add(1);
  } else {
    failedRequests.add(1);
    requestRate.add(0);
    if (response.status !== 200) {
      console.log(`Failed request to /ping: ${response.status} in ${response.timings.duration}ms`);
    }
  }
  
  // 不添加思考时间，以最大化并发压力
  // sleep(0);
}
