import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  // 预热阶段
  stages: [
    { duration: '10s', target: 1000 }, // 逐渐增加到 1000 个虚拟用户
    { duration: '20s', target: 5000 }, // 逐渐增加到 5000 个虚拟用户
    { duration: '30s', target: 10000 }, // 逐渐增加到 10000 个虚拟用户
    { duration: '60s', target: 10000 }, // 保持 10000 个虚拟用户 60 秒
    { duration: '10s', target: 0 }, // 逐渐减少到 0 个虚拟用户
  ],
  thresholds: {
    http_req_duration: ['p(95)<200'], // 95% 的请求应该在 200ms 内完成
    http_req_failed: ['rate<0.01'], // 请求失败率应该小于 1%
  },
};

export default function () {
  const url = 'http://localhost:8080/ping';
  const res = http.get(url);
  
  check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 200ms': (r) => r.timings.duration < 200,
  });
  
  sleep(0.1); // 每个虚拟用户之间的间隔时间
}
