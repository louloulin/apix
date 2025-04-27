# APIX - AI Agent Gateway

APIX is a high-performance AI Agent Gateway built on GraalVM and Vert.x, designed to manage, secure, and optimize AI agent interactions. Inspired by Kong Gateway's plugin architecture, APIX provides a flexible and extensible platform for routing, transforming, and monitoring AI agent traffic.

## Features

- **High Performance**: Built on Vert.x reactive framework and GraalVM for native compilation
- **Plugin System**: Modular design with hot-swappable plugins
- **AI-Specific Features**: AI model routing, prompt transformation, response caching, and token usage tracking
- **Admin API**: RESTful API for managing routes, services, and plugins
- **Native Compilation**: GraalVM native image support for low memory footprint and fast startup

## Getting Started

### Prerequisites

- JDK 17 or later
- GraalVM CE 22.3 or later (for native compilation)
- Gradle 8.0 or later

### Building the Project

```bash
# Build the project
./gradlew build

# Build native image
./gradlew nativeCompile
```

### Running the Gateway

```bash
# Run in JVM mode
./gradlew run

# Run native image
./build/native/nativeCompile/apix
```

## Configuration

APIX is configured using a JSON file located at `config/apix.json`. You can customize the gateway by modifying this file.

Example configuration:

```json
{
  "gateway": {
    "host": "0.0.0.0",
    "port": 8080
  },
  "admin": {
    "enabled": true,
    "host": "0.0.0.0",
    "port": 8081
  },
  "plugins": [
    {
      "id": "rate-limiter",
      "type": "rate-limiter",
      "config": {
        "limit": 100,
        "window": 60
      }
    }
  ],
  "routes": [
    {
      "id": "openai-chat",
      "name": "OpenAI Chat Completions",
      "path": "/v1/chat/completions",
      "methods": ["POST"],
      "targetUrl": "https://api.openai.com/v1/chat/completions",
      "plugins": ["rate-limiter"],
      "enabled": true
    }
  ]
}
```

## API Reference

### Admin API

#### Routes Management
```
GET    /admin/routes                # List all routes
POST   /admin/routes                # Create a new route
GET    /admin/routes/{id}           # Get route details
PUT    /admin/routes/{id}           # Update a route
DELETE /admin/routes/{id}           # Delete a route
```

#### Plugins Management
```
GET    /admin/plugins               # List all plugins
```

#### AI-Specific Endpoints
```
GET    /admin/ai/models             # List available AI models
GET    /admin/ai/usage              # Get token usage statistics
```

## Plugin System

APIX includes a flexible plugin system that allows you to extend the gateway's functionality. Plugins can be used to add authentication, rate limiting, request/response transformation, and more.

### Core Plugins

- **Authentication**: API Key, JWT, OAuth2
- **Security**: Rate Limiting, IP Restriction, Request Validation
- **Transformation**: Request/Response Transformation, Prompt Template Injection
- **Logging & Monitoring**: Request Logging, Metrics Collection, Distributed Tracing

### AI-Specific Plugins

- **Prompt Management**: Prompt Validation, Prompt Transformation, Context Window Management
- **AI Response Handling**: Response Caching, Token Usage Tracking, Content Filtering
- **AI Service Management**: Model Routing, Load Balancing, Fallback Strategies

## License

This project is licensed under the Apache License 2.0 - see the LICENSE file for details.
