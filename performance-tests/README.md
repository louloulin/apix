# APIX Gateway Performance Tests

This directory contains performance testing tools for the APIX Gateway.

## Locust Performance Tests

[Locust](https://locust.io/) is a user-friendly, scriptable and scalable performance testing tool.

### Prerequisites

- Python 3.7+
- pip

### Installation

Install Locust and dependencies:

```bash
pip install locust
```

### Running the Tests

1. Start the APIX Gateway
2. Navigate to the `performance-tests` directory
3. Run Locust:

```bash
locust -f locustfile.py --host=http://localhost:8080
```

4. Open your browser and go to `http://localhost:8089`
5. Configure the test parameters:
   - Number of users: The number of concurrent users
   - Spawn rate: How many users to add per second
   - Host: The URL of your APIX Gateway (should be pre-filled)

6. Start the test and monitor the results

### Test Scenarios

The Locust file includes two user classes:

1. **APIXGatewayUser**: Simulates a typical user with a mix of requests:
   - Chat completion requests (higher frequency)
   - Admin API requests for models and usage statistics (lower frequency)

2. **HighVolumeUser**: Simulates a high-volume user with:
   - Very frequent chat completion requests
   - Minimal wait time between requests

### Customizing Tests

You can modify the `locustfile.py` to:

- Change the API key
- Adjust the wait times between requests
- Modify the sample prompts
- Add new request types
- Change the task weights

### Interpreting Results

Locust provides several metrics:

- **Request Count**: Total number of requests made
- **Response Time**: Min, max, average, and median response times
- **Request Rate**: Requests per second
- **Failure Rate**: Percentage of failed requests
- **Custom Metrics**: Token usage statistics

### Load Testing Best Practices

1. Start with a small number of users and gradually increase
2. Monitor the gateway's resource usage during tests
3. Test with realistic scenarios and data
4. Run tests on a separate machine from the gateway
5. Consider the impact on any external services (like OpenAI API)

## Other Performance Testing Tools

The repository also includes:

- `performance-test.sh`: A simple bash script using Apache Bench for quick tests
- Docker Compose setup with Prometheus and Grafana for monitoring

## Notes

- Be mindful of rate limits on external APIs when running performance tests
- Consider using mock responses for high-volume testing
- Always test in a controlled environment before running tests against production
