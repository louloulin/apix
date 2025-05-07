import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

// 自定义指标
const successRate = new Rate('success_rate');
const errorRate = new Rate('error_rate');
const responseTime = new Trend('response_time');
const requestsPerSecond = new Counter('requests_per_second');

// 添加更详细的指标
const p90ResponseTime = new Trend('p90_response_time');
const p95ResponseTime = new Trend('p95_response_time');
const p99ResponseTime = new Trend('p99_response_time');
const activeConnections = new Counter('active_connections');
const iterationDuration = new Trend('iteration_duration');

// 测试配置 - 使用阶段性增加负载的方式，包含预热阶段
export const options = {
  // 系统资源配置
  discardResponseBodies: true,  // 丢弃响应体以节省内存
  noConnectionReuse: false,     // 重用连接以最大化并发

  // 阈值配置 - 更详细的指标
  thresholds: {
    http_req_duration: ['p(95)<100'], // 95% 的请求响应时间小于 100ms
    http_req_failed: ['rate<0.01'],   // 请求失败率小于 1%
    'response_time': ['p(90)<80', 'p(95)<100', 'p(99)<200'], // 更详细的响应时间阈值
    'success_rate': ['rate>0.99'],    // 成功率大于 99%
    'iteration_duration': ['p(95)<200'], // 整个迭代时间小于 200ms
  },

  // 使用场景配置 - 分阶段增加负载
  scenarios: {
    ramping_load: {
      executor: 'ramping-vus',
      startVUs: 10,
      stages: [
        // 预热阶段 - 逐步增加到100用户
        { duration: '10s', target: 100 },  // 10秒内增加到100用户
        { duration: '10s', target: 100 },  // 保持100用户10秒

        // 中等负载阶段 - 500用户
        { duration: '10s', target: 500 },  // 10秒内增加到500用户
        { duration: '20s', target: 500 },  // 保持500用户20秒

        // 高负载阶段 - 1000用户
        { duration: '10s', target: 1000 }, // 10秒内增加到1000用户
        { duration: '30s', target: 1000 }, // 保持1000用户30秒

        // 冷却阶段 - 逐步减少负载
        { duration: '10s', target: 100 },  // 10秒内减少到100用户
        { duration: '5s', target: 0 }      // 5秒内减少到0用户
      ]
    }
  }
};

// 测试函数 - 最简单的请求
export default function() {
  // 记录开始时间
  const startTime = new Date().getTime();

  // 记录活动连接数
  activeConnections.add(1);

  try {
    // 发送请求到正确的端点
    const response = http.get('http://localhost:8080/api/hello');

    // 记录响应时间
    const duration = response.timings.duration;
    responseTime.add(duration);

    // 记录百分位指标
    p90ResponseTime.add(duration);
    p95ResponseTime.add(duration);
    p99ResponseTime.add(duration);

    // 检查响应 - 只检查状态码
    const success = check(response, {
      'status is 200': (r) => r.status === 200,
    });

    // 更新指标
    successRate.add(success);
    errorRate.add(!success);
    requestsPerSecond.add(1);

    // 动态休眠时间 - 根据当前VU数量调整
    // 当VU数量较少时休眠更长时间，VU数量较多时休眠更短时间
    const currentVUs = __VU;
    let sleepTime = 0.01; // 默认10ms

    if (currentVUs < 100) {
      sleepTime = 0.05; // 50ms for warm-up phase
    } else if (currentVUs < 500) {
      sleepTime = 0.02; // 20ms for medium load
    } else {
      sleepTime = 0.005; // 5ms for high load
    }

    sleep(sleepTime);

    // 记录整个迭代持续时间
    const endTime = new Date().getTime();
    const iterTime = endTime - startTime;
    iterationDuration.add(iterTime);

  } catch (e) {
    console.error(`请求失败: ${e.message}`);
    errorRate.add(1);
  } finally {
    // 减少活动连接数
    activeConnections.add(-1);
  }
}

// 测试完成后的汇总报告
export function handleSummary(data) {
  console.log('\n\n性能测试汇总报告:');
  console.log(`总请求数: ${data.metrics.http_reqs.values.count}`);
  console.log(`平均响应时间: ${data.metrics.http_req_duration.values.avg.toFixed(2)}ms`);
  console.log(`响应时间第90百分位: ${data.metrics.http_req_duration.values['p(90)'].toFixed(2)}ms`);
  console.log(`响应时间第95百分位: ${data.metrics.http_req_duration.values['p(95)'].toFixed(2)}ms`);
  console.log(`响应时间第99百分位: ${data.metrics.http_req_duration.values['p(99)'].toFixed(2)}ms`);
  console.log(`请求失败率: ${(data.metrics.http_req_failed.values.rate * 100).toFixed(2)}%`);
  console.log(`每秒请求数: ${(data.metrics.http_reqs.values.count / (data.state.testRunDurationMs / 1000)).toFixed(2)} RPS`);

  // 返回文本和JSON格式的汇总报告
  return {
    'stdout': textSummary(data, { indent: ' ', enableColors: true }),
    'summary.json': JSON.stringify(data, null, 2),
    'summary.html': generateHtmlReport(data)
  };
}

// 生成HTML报告
function generateHtmlReport(data) {
  return `
<!DOCTYPE html>
<html>
<head>
  <title>APIX Gateway 性能测试报告</title>
  <meta charset="UTF-8">
  <style>
    body { font-family: Arial, sans-serif; margin: 20px; }
    h1 { color: #2c3e50; }
    .summary { background-color: #f8f9fa; padding: 15px; border-radius: 5px; }
    .metrics { margin-top: 20px; }
    table { border-collapse: collapse; width: 100%; }
    th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
    th { background-color: #4CAF50; color: white; }
    tr:nth-child(even) { background-color: #f2f2f2; }
    .good { color: green; }
    .warning { color: orange; }
    .bad { color: red; }
  </style>
</head>
<body>
  <h1>APIX Gateway 性能测试报告</h1>

  <div class="summary">
    <h2>测试概要</h2>
    <p><strong>测试时间:</strong> ${new Date().toLocaleString()}</p>
    <p><strong>测试持续时间:</strong> ${(data.state.testRunDurationMs / 1000).toFixed(1)} 秒</p>
    <p><strong>总请求数:</strong> ${data.metrics.http_reqs.values.count}</p>
    <p><strong>每秒请求数:</strong> ${(data.metrics.http_reqs.values.count / (data.state.testRunDurationMs / 1000)).toFixed(2)} RPS</p>
    <p><strong>请求失败率:</strong> ${(data.metrics.http_req_failed.values.rate * 100).toFixed(2)}%</p>
  </div>

  <div class="metrics">
    <h2>详细指标</h2>
    <table>
      <tr>
        <th>指标</th>
        <th>平均值</th>
        <th>最小值</th>
        <th>最大值</th>
        <th>p(90)</th>
        <th>p(95)</th>
        <th>p(99)</th>
      </tr>
      <tr>
        <td>响应时间 (ms)</td>
        <td>${data.metrics.http_req_duration.values.avg.toFixed(2)}</td>
        <td>${data.metrics.http_req_duration.values.min.toFixed(2)}</td>
        <td>${data.metrics.http_req_duration.values.max.toFixed(2)}</td>
        <td>${data.metrics.http_req_duration.values['p(90)'].toFixed(2)}</td>
        <td>${data.metrics.http_req_duration.values['p(95)'].toFixed(2)}</td>
        <td>${data.metrics.http_req_duration.values['p(99)'].toFixed(2)}</td>
      </tr>
      <tr>
        <td>连接时间 (ms)</td>
        <td>${data.metrics.http_req_connecting.values.avg.toFixed(2)}</td>
        <td>${data.metrics.http_req_connecting.values.min.toFixed(2)}</td>
        <td>${data.metrics.http_req_connecting.values.max.toFixed(2)}</td>
        <td>${data.metrics.http_req_connecting.values['p(90)'].toFixed(2)}</td>
        <td>${data.metrics.http_req_connecting.values['p(95)'].toFixed(2)}</td>
        <td>${data.metrics.http_req_connecting.values['p(99)'].toFixed(2)}</td>
      </tr>
      <tr>
        <td>等待时间 (ms)</td>
        <td>${data.metrics.http_req_waiting.values.avg.toFixed(2)}</td>
        <td>${data.metrics.http_req_waiting.values.min.toFixed(2)}</td>
        <td>${data.metrics.http_req_waiting.values.max.toFixed(2)}</td>
        <td>${data.metrics.http_req_waiting.values['p(90)'].toFixed(2)}</td>
        <td>${data.metrics.http_req_waiting.values['p(95)'].toFixed(2)}</td>
        <td>${data.metrics.http_req_waiting.values['p(99)'].toFixed(2)}</td>
      </tr>
    </table>
  </div>

  <div class="conclusion">
    <h2>测试结论</h2>
    <p>
      ${data.metrics.http_req_duration.values['p(95)'] < 100 ?
        '<span class="good">系统性能良好: 95%的请求响应时间小于100ms</span>' :
        '<span class="warning">系统性能需要改进: 95%的请求响应时间大于100ms</span>'}
    </p>
    <p>
      ${data.metrics.http_req_failed.values.rate < 0.01 ?
        '<span class="good">系统稳定性良好: 请求失败率小于1%</span>' :
        '<span class="bad">系统稳定性需要改进: 请求失败率大于1%</span>'}
    </p>
  </div>
</body>
</html>
  `;
}