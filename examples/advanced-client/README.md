# APIX Gateway Advanced Client

This is an advanced web client that demonstrates how to use the APIX Gateway to interact with AI models and utilize the Admin API.

## Features

- Chat interface for interacting with AI models
- Token usage statistics visualization
- Admin controls for cache management
- Model information display
- Server-side API proxy to handle authentication and requests
- Responsive UI with tabbed interface

## Prerequisites

- Node.js 14.x or later
- npm or yarn
- APIX Gateway running

## Installation

1. Clone the repository
2. Navigate to the `examples/advanced-client` directory
3. Copy `.env.example` to `.env` and update the configuration
4. Install dependencies:

```bash
npm install
```

5. Start the server:

```bash
npm start
```

6. Open your browser and navigate to `http://localhost:3000`

## Configuration

Edit the `.env` file to configure the application:

```
# APIX Gateway configuration
APIX_GATEWAY_URL=http://localhost:8080
APIX_ADMIN_URL=http://localhost:8081
APIX_API_KEY=test-key

# Server configuration
PORT=3000
```

## Usage

### Chat Tab

- Select an AI model from the dropdown
- Type your message in the input field
- Press Enter or click the Send button
- View the AI's response in the chat window

### Usage Stats Tab

- View token usage statistics
- See total tokens used, prompt tokens, and completion tokens
- View usage by model
- View daily usage statistics
- Click "Refresh" to update the statistics

### Admin Tab

- Clear the response cache to ensure fresh responses
- View available AI models
- Click "Refresh" to update the model list

## Architecture

This application consists of:

1. **Express.js Server**: Handles API requests and serves the web interface
2. **EJS Templates**: Renders the HTML for the web interface
3. **Client-side JavaScript**: Handles user interactions and updates the UI
4. **CSS Styling**: Provides a responsive and user-friendly interface

The server acts as a proxy between the client and the APIX Gateway, handling authentication and request formatting.

## License

This example is part of the APIX Gateway project and is licensed under the same terms.
