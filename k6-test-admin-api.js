import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 50 },  // 逐渐增加到 50 个虚拟用户
    { duration: '1m', target: 50 },   // 保持 50 个虚拟用户 1 分钟
    { duration: '30s', target: 0 },   // 逐渐减少到 0 个虚拟用户
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% 的请求应该在 500ms 内完成
    http_req_failed: ['rate<0.01'],   // 请求失败率应该小于 1%
  },
};

export default function () {
  // 测试 Admin API 的配置端点
  const configRes = http.get('http://localhost:8080/admin/config');
  check(configRes, {
    'config status is 200': (r) => r.status === 200,
    'config has correct content-type': (r) => r.headers['Content-Type'] === 'application/json',
    'config body contains gateway': (r) => r.body.includes('gateway'),
  });

  // 测试 Admin API 的路由端点
  const routesRes = http.get('http://localhost:8080/admin/routes');
  check(routesRes, {
    'routes status is 200': (r) => r.status === 200,
    'routes has correct content-type': (r) => r.headers['Content-Type'] === 'application/json',
    'routes body contains routes': (r) => r.body.includes('routes'),
  });

  // 测试 Admin API 的插件端点
  const pluginsRes = http.get('http://localhost:8080/admin/plugins');
  check(pluginsRes, {
    'plugins status is 200': (r) => r.status === 200,
    'plugins has correct content-type': (r) => r.headers['Content-Type'] === 'application/json',
    'plugins body contains plugins': (r) => r.body.includes('plugins'),
  });

  // 测试 Admin API 的服务端点
  const servicesRes = http.get('http://localhost:8080/admin/services');
  check(servicesRes, {
    'services status is 200': (r) => r.status === 200,
    'services has correct content-type': (r) => r.headers['Content-Type'] === 'application/json',
    'services body contains services': (r) => r.body.includes('services'),
  });

  // 在每次迭代之间休息一下
  sleep(1);
}
