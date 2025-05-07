import http from 'k6/http';
import { sleep, check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// 自定义指标
const successCounter = new Counter('success_counter');
const failureCounter = new Counter('failure_counter');
const successRate = new Rate('success_rate');
const requestTrend = new Trend('request_trend');

// 测试配置 - 高性能测试
export const options = {
  // 预热阶段和负载阶段
  stages: [
    { duration: '5s', target: 500 },     // 预热：快速增加到500个虚拟用户
    { duration: '10s', target: 2000 },   // 增加负载：在10秒内增加到2000个虚拟用户
    { duration: '30s', target: 5000 },   // 增加负载：在30秒内增加到5000个虚拟用户
    { duration: '30s', target: 5000 },   // 稳定负载：保持5000个虚拟用户30秒
    { duration: '5s', target: 0 },       // 冷却：快速减少到0个虚拟用户
  ],
  thresholds: {
    http_req_duration: ['p(95)<50'],     // 95%的请求应该在50ms内完成
    http_req_failed: ['rate<0.01'],      // 请求失败率应该小于1%
    'success_rate': ['rate>0.99'],       // 成功率应该大于99%
  },
};

// 测试端点
const API_BASE_URL = 'http://localhost:8080';

// 测试主函数 - 只测试最高性能的端点
export default function() {
  // 测试超高性能端点
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
  
  // 测试基准测试端点
  const benchRes = http.get(`${API_BASE_URL}/bench`);
  
  // 检查响应是否成功
  const benchCheck = check(benchRes, {
    'bench status is 200': (r) => r.status === 200,
    'bench response is OK': (r) => r.body === 'OK',
  });
  
  successRate.add(benchCheck);
  
  if (benchCheck) {
    successCounter.add(1);
  } else {
    failureCounter.add(1);
  }
  
  // 不添加延迟，以获得最大吞吐量
}
