const express = require('express');
const axios = require('axios');
const bodyParser = require('body-parser');
const dotenv = require('dotenv');
const path = require('path');

// Load environment variables
dotenv.config();

const app = express();
const port = process.env.PORT || 3000;
const gatewayUrl = process.env.APIX_GATEWAY_URL || 'http://localhost:8080';
const adminUrl = process.env.APIX_ADMIN_URL || 'http://localhost:8081';
const apiKey = process.env.APIX_API_KEY || 'test-key';

// Middleware
app.use(bodyParser.json());
app.use(bodyParser.urlencoded({ extended: true }));
app.use(express.static(path.join(__dirname, 'public')));

// Set view engine
app.set('view engine', 'ejs');
app.set('views', path.join(__dirname, 'views'));

// Routes
app.get('/', (req, res) => {
    res.render('index', { gatewayUrl, adminUrl, apiKey });
});

// API routes
app.post('/api/chat', async (req, res) => {
    try {
        const { model, messages } = req.body;
        
        // Determine the endpoint based on the model
        let endpoint = '/v1/chat/completions';
        let requestBody = {
            model,
            messages
        };
        
        if (model.startsWith('claude')) {
            endpoint = '/v1/messages';
            requestBody = {
                model,
                messages: messages.map(msg => ({
                    role: msg.role === 'assistant' ? 'assistant' : 'user',
                    content: msg.content
                }))
            };
        }
        
        // Send the request to the gateway
        const response = await axios.post(`${gatewayUrl}${endpoint}`, requestBody, {
            headers: {
                'Content-Type': 'application/json',
                'X-API-Key': apiKey
            }
        });
        
        // Extract the AI response
        let aiMessage;
        if (model.startsWith('claude')) {
            aiMessage = response.data.content[0].text;
        } else {
            aiMessage = response.data.choices[0].message.content;
        }
        
        res.json({ message: aiMessage });
    } catch (error) {
        console.error('Error:', error.response?.data || error.message);
        res.status(500).json({ error: error.response?.data?.error || error.message });
    }
});

app.get('/api/models', async (req, res) => {
    try {
        const response = await axios.get(`${adminUrl}/admin/ai/models`, {
            headers: {
                'X-API-Key': apiKey
            }
        });
        
        res.json(response.data);
    } catch (error) {
        console.error('Error:', error.response?.data || error.message);
        res.status(500).json({ error: error.response?.data?.error || error.message });
    }
});

app.get('/api/usage', async (req, res) => {
    try {
        const response = await axios.get(`${adminUrl}/admin/ai/usage`, {
            headers: {
                'X-API-Key': apiKey
            }
        });
        
        res.json(response.data);
    } catch (error) {
        console.error('Error:', error.response?.data || error.message);
        res.status(500).json({ error: error.response?.data?.error || error.message });
    }
});

app.post('/api/cache/clear', async (req, res) => {
    try {
        const response = await axios.post(`${adminUrl}/admin/ai/cache/clear`, {}, {
            headers: {
                'X-API-Key': apiKey
            }
        });
        
        res.json(response.data);
    } catch (error) {
        console.error('Error:', error.response?.data || error.message);
        res.status(500).json({ error: error.response?.data?.error || error.message });
    }
});

// Start the server
app.listen(port, () => {
    console.log(`Server running at http://localhost:${port}`);
});
