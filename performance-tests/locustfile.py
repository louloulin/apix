import json
import random
import time
from locust import HttpUser, task, between

# Sample prompts for testing
SAMPLE_PROMPTS = [
    "What is the capital of France?",
    "Explain quantum computing in simple terms.",
    "Write a short poem about technology.",
    "What are the benefits of exercise?",
    "How does photosynthesis work?",
    "Tell me about the history of the internet.",
    "What are the main features of Python?",
    "Explain the theory of relativity.",
    "What is artificial intelligence?",
    "How do electric cars work?"
]

class APIXGatewayUser(HttpUser):
    wait_time = between(1, 5)  # Wait between 1 and 5 seconds between tasks
    
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.api_key = "test-key"  # Default API key
    
    def on_start(self):
        # This method is called when a user starts
        pass
    
    @task(3)
    def chat_completion(self):
        # OpenAI-style chat completion endpoint
        prompt = random.choice(SAMPLE_PROMPTS)
        
        headers = {
            "Content-Type": "application/json",
            "X-API-Key": self.api_key
        }
        
        payload = {
            "model": "gpt-3.5-turbo",
            "messages": [
                {"role": "user", "content": prompt}
            ]
        }
        
        start_time = time.time()
        with self.client.post("/v1/chat/completions", 
                             json=payload, 
                             headers=headers, 
                             catch_response=True) as response:
            duration = time.time() - start_time
            
            if response.status_code == 200:
                response.success()
                try:
                    data = response.json()
                    # Log token usage if available
                    if "usage" in data:
                        tokens = data["usage"]["total_tokens"]
                        self.environment.events.request.fire(
                            request_type="TOKEN_USAGE",
                            name="Total Tokens",
                            response_time=tokens,
                            response_length=0,
                            exception=None,
                            context={}
                        )
                except json.JSONDecodeError:
                    response.failure("Invalid JSON response")
            elif response.status_code == 429:
                response.failure("Rate limit exceeded")
            else:
                response.failure(f"Unexpected status code: {response.status_code}")
    
    @task(1)
    def get_models(self):
        # Admin API endpoint to get available models
        headers = {
            "X-API-Key": self.api_key
        }
        
        with self.client.get("/admin/ai/models", 
                            headers=headers, 
                            catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Unexpected status code: {response.status_code}")
    
    @task(1)
    def get_usage_stats(self):
        # Admin API endpoint to get usage statistics
        headers = {
            "X-API-Key": self.api_key
        }
        
        with self.client.get("/admin/ai/usage", 
                            headers=headers, 
                            catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Unexpected status code: {response.status_code}")

class HighVolumeUser(APIXGatewayUser):
    wait_time = between(0.1, 1)  # Much shorter wait time for high volume testing
    
    @task
    def chat_completion_high_volume(self):
        # Same as chat_completion but with a fixed prompt for consistency
        headers = {
            "Content-Type": "application/json",
            "X-API-Key": self.api_key
        }
        
        payload = {
            "model": "gpt-3.5-turbo",
            "messages": [
                {"role": "user", "content": "Hello, how are you?"}
            ]
        }
        
        with self.client.post("/v1/chat/completions", 
                             json=payload, 
                             headers=headers, 
                             catch_response=True) as response:
            if response.status_code == 200:
                response.success()
            elif response.status_code == 429:
                response.failure("Rate limit exceeded")
            else:
                response.failure(f"Unexpected status code: {response.status_code}")
