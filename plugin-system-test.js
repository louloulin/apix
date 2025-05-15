// Plugin System Optimization Test Script
const http = require('http');

// Configuration
const HOST = 'localhost';
const PORT = 8080;
const BASE_PATH = '/api';

// Test plugin IDs
const TEST_PLUGINS = [
  'auth-plugin',
  'rate-limit-plugin',
  'logging-plugin',
  'transform-plugin',
  'cache-plugin'
];

// Helper function to make HTTP requests
function makeRequest(method, path, data = null) {
  return new Promise((resolve, reject) => {
    const options = {
      hostname: HOST,
      port: PORT,
      path: `${BASE_PATH}${path}`,
      method: method,
      headers: {
        'Content-Type': 'application/json'
      }
    };

    const req = http.request(options, (res) => {
      let responseData = '';
      
      res.on('data', (chunk) => {
        responseData += chunk;
      });
      
      res.on('end', () => {
        try {
          const parsedData = JSON.parse(responseData);
          resolve(parsedData);
        } catch (e) {
          resolve(responseData);
        }
      });
    });
    
    req.on('error', (error) => {
      reject(error);
    });
    
    if (data) {
      req.write(JSON.stringify(data));
    }
    
    req.end();
  });
}

// Test functions
async function testPluginExecution() {
  console.log('Testing single plugin execution...');
  
  const pluginId = TEST_PLUGINS[0];
  const context = {
    request: {
      path: '/test',
      method: 'GET',
      headers: {
        'user-agent': 'plugin-system-test'
      }
    },
    response: {
      statusCode: 200,
      headers: {}
    }
  };
  
  try {
    const result = await makeRequest('POST', '/plugins/execute', {
      pluginId: pluginId,
      context: context
    });
    
    console.log('Plugin execution result:', JSON.stringify(result, null, 2));
    return true;
  } catch (error) {
    console.error('Error executing plugin:', error);
    return false;
  }
}

async function testPluginChainExecution() {
  console.log('Testing plugin chain execution...');
  
  const context = {
    request: {
      path: '/test',
      method: 'GET',
      headers: {
        'user-agent': 'plugin-system-test'
      }
    },
    response: {
      statusCode: 200,
      headers: {}
    }
  };
  
  try {
    const result = await makeRequest('POST', '/plugins/chain/execute', {
      pluginIds: TEST_PLUGINS,
      context: context
    });
    
    console.log('Plugin chain execution result:', JSON.stringify(result, null, 2));
    return true;
  } catch (error) {
    console.error('Error executing plugin chain:', error);
    return false;
  }
}

async function testGetStats() {
  console.log('Testing get stats...');
  
  try {
    const result = await makeRequest('GET', '/plugins/system/stats');
    console.log('Plugin system stats:', JSON.stringify(result, null, 2));
    return true;
  } catch (error) {
    console.error('Error getting stats:', error);
    return false;
  }
}

async function testGetConfig() {
  console.log('Testing get config...');
  
  try {
    const result = await makeRequest('GET', '/plugins/system/config');
    console.log('Plugin system config:', JSON.stringify(result, null, 2));
    return true;
  } catch (error) {
    console.error('Error getting config:', error);
    return false;
  }
}

async function testGetStatus() {
  console.log('Testing get status...');
  
  try {
    const result = await makeRequest('GET', '/plugins/system/status');
    console.log('Plugin system status:', JSON.stringify(result, null, 2));
    return true;
  } catch (error) {
    console.error('Error getting status:', error);
    return false;
  }
}

// Performance test
async function testPerformance() {
  console.log('Running performance test...');
  
  const iterations = 100;
  const context = {
    request: {
      path: '/test',
      method: 'GET',
      headers: {
        'user-agent': 'plugin-system-test'
      }
    },
    response: {
      statusCode: 200,
      headers: {}
    }
  };
  
  const startTime = Date.now();
  
  const promises = [];
  for (let i = 0; i < iterations; i++) {
    promises.push(makeRequest('POST', '/plugins/chain/execute', {
      pluginIds: TEST_PLUGINS,
      context: context
    }));
  }
  
  try {
    await Promise.all(promises);
    const endTime = Date.now();
    const duration = endTime - startTime;
    const requestsPerSecond = (iterations / (duration / 1000)).toFixed(2);
    
    console.log(`Performance test completed:`);
    console.log(`- Iterations: ${iterations}`);
    console.log(`- Total duration: ${duration}ms`);
    console.log(`- Average duration per request: ${(duration / iterations).toFixed(2)}ms`);
    console.log(`- Requests per second: ${requestsPerSecond}`);
    
    // Get final stats
    await testGetStats();
    
    return true;
  } catch (error) {
    console.error('Error in performance test:', error);
    return false;
  }
}

// Run all tests
async function runTests() {
  console.log('Starting plugin system optimization tests...');
  
  // Basic tests
  await testGetConfig();
  console.log('-----------------------------------');
  
  await testGetStatus();
  console.log('-----------------------------------');
  
  await testPluginExecution();
  console.log('-----------------------------------');
  
  await testPluginChainExecution();
  console.log('-----------------------------------');
  
  await testGetStats();
  console.log('-----------------------------------');
  
  // Performance test
  await testPerformance();
  console.log('-----------------------------------');
  
  console.log('All tests completed!');
}

// Run the tests
runTests().catch(console.error);
