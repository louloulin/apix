#!/bin/bash

# Performance test script for APIX Gateway
# This script uses Apache Bench (ab) to test the performance of the gateway

# Configuration
HOST="localhost"
PORT="8080"
ENDPOINT="/v1/chat/completions"
CONCURRENCY=10
REQUESTS=1000
API_KEY="test-key"

# Check if Apache Bench is installed
if ! command -v ab &> /dev/null; then
    echo "Apache Bench (ab) is not installed. Please install it first."
    echo "On Ubuntu/Debian: sudo apt-get install apache2-utils"
    echo "On macOS: brew install httpd"
    exit 1
fi

# Create a temporary file for the request body
TEMP_FILE=$(mktemp)

# Write the request body to the temporary file
cat > $TEMP_FILE << EOF
{
  "model": "gpt-3.5-turbo",
  "messages": [
    {
      "role": "user",
      "content": "Hello, how are you?"
    }
  ]
}
EOF

echo "Starting performance test..."
echo "Host: $HOST:$PORT"
echo "Endpoint: $ENDPOINT"
echo "Concurrency: $CONCURRENCY"
echo "Requests: $REQUESTS"
echo ""

# Run the test
ab -n $REQUESTS -c $CONCURRENCY -H "Content-Type: application/json" -H "X-API-Key: $API_KEY" -p $TEMP_FILE "http://$HOST:$PORT$ENDPOINT"

# Clean up
rm $TEMP_FILE

echo "Performance test completed."
