import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// 自定义指标
const successRate = new Rate('success_rate');
const errorRate = new Rate('error_rate');
const responseTime = new Trend('response_time');
const requestsPerSecond = new Counter('requests_per_second');

// 测试配置 - 阶段性增加负载
export const options = {
  // 使用场景配置进行更精细的控制
  scenarios: {
    ramp_up: {
      executor: 'ramping-vus',
      startVUs: 100,
      stages: [
        { duration: '10s', target: 100 },   // 从100用户开始
        { duration: '20s', target: 1000 },  // 10秒内增加到1000用户
        { duration: '30s', target: 5000 },  // 20秒内增加到5000用户
        { duration: '30s', target: 10000 }, // 30秒内增加到10000用户
        { duration: '30s', target: 10000 }, // 保持10000用户30秒
        { duration: '20s', target: 1000 },  // 20秒内减少到1000用户
        { duration: '10s', target: 100 }    // 10秒内减少到100用户
      ],
      gracefulRampDown: '5s'
    }
  },
  
  // 系统资源配置
  discardResponseBodies: true,  // 丢弃响应体以节省内存
  noConnectionReuse: false,     // 重用连接以最大化并发
  
  // 阈值配置
  thresholds: {
    http_req_duration: ['p(95)<100'], // 95% 的请求响应时间小于 100ms
    http_req_failed: ['rate<0.01'],   // 请求失败率小于 1%
    'response_time': ['p(95)<100'],   // 自定义响应时间指标
    'success_rate': ['rate>0.99'],    // 成功率大于 99%
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

// 测试开始时执行
export function setup() {
  console.log('开始 Native Image 性能测试');
}

// 测试结束时执行
export function teardown(data) {
  console.log('Native Image 性能测试完成');
}
