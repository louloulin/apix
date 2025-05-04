import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Counter, Trend } from 'k6/metrics';

// 自定义指标
const successRate = new Rate('success_rate');
const errorRate = new Rate('error_rate');
const responseTime = new Trend('response_time');
const requestsPerSecond = new Counter('requests_per_second');

// 测试配置
export const options = {
  // 阶段式负载
  stages: [
    { duration: '10s', target: 100 },   // 10秒内增加到 100 并发用户
    { duration: '20s', target: 500 },   // 20秒内增加到 500 并发用户
    { duration: '30s', target: 1000 },  // 30秒内增加到 1000 并发用户
    { duration: '60s', target: 1000 },  // 维持 1000 并发用户 1 分钟
    { duration: '10s', target: 0 },     // 10秒内减少到 0 并发用户
  ],
  
  // 阈值设置
  thresholds: {
    http_req_duration: ['p(95)<50'], // 95% 的请求响应时间小于 50ms
    'success_rate': ['rate>0.99'],   // 成功率大于 99%
    'error_rate': ['rate<0.01'],     // 错误率小于 1%
  },
};

// 测试函数
export default function() {
  // 发送请求
  const startTime = new Date().getTime();
  const response = http.get('http://localhost:8080/api/hello');
  const endTime = new Date().getTime();
  
  // 计算响应时间
  const duration = endTime - startTime;
  responseTime.add(duration);
  
  // 检查响应
  const success = response.status === 200;
  const error = !success;
  
  // 更新指标
  successRate.add(success);
  errorRate.add(error);
  requestsPerSecond.add(1);
  
  // 检查响应
  check(response, {
    'status is 200': (r) => r.status === 200,
    'response is valid JSON': (r) => {
      try {
        JSON.parse(r.body);
        return true;
      } catch (e) {
        return false;
      }
    },
    'response contains message': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.message !== undefined;
      } catch (e) {
        return false;
      }
    }
  });
  
  // 不休眠，最大压力测试
}
