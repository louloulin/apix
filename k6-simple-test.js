import http from 'k6/http';
import { check, sleep } from 'k6';

// 简单的测试配置
export const options = {
  vus: 10,           // 10个虚拟用户
  duration: '30s',   // 测试持续30秒
};

// 测试函数
export default function() {
  const response = http.get('http://localhost:8080/api/hello');

  // 检查响应
  check(response, {
    'status is 200': (r) => r.status === 200,
  });

  // 休眠0.1秒
  sleep(0.1);
}
