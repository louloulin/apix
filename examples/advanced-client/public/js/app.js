// DOM Elements
const tabButtons = document.querySelectorAll('.tab-button');
const tabContents = document.querySelectorAll('.tab-content');
const messagesContainer = document.getElementById('messages');
const userInput = document.getElementById('user-input');
const sendButton = document.getElementById('send-button');
const gatewayUrlInput = document.getElementById('gateway-url');
const apiKeyInput = document.getElementById('api-key');
const modelSelect = document.getElementById('model');
const refreshUsageButton = document.getElementById('refresh-usage');
const totalStatsContainer = document.getElementById('total-stats');
const modelStatsContainer = document.getElementById('model-stats');
const dailyStatsContainer = document.getElementById('daily-stats');
const clearCacheButton = document.getElementById('clear-cache');
const cacheStatusContainer = document.getElementById('cache-status');
const refreshModelsButton = document.getElementById('refresh-models');
const modelsListContainer = document.getElementById('models-list');

// State
let messages = [];
let isProcessing = false;

// Tab functionality
tabButtons.forEach(button => {
    button.addEventListener('click', () => {
        // Remove active class from all buttons and contents
        tabButtons.forEach(btn => btn.classList.remove('active'));
        tabContents.forEach(content => content.classList.remove('active'));
        
        // Add active class to clicked button and corresponding content
        button.classList.add('active');
        const tabId = button.dataset.tab;
        document.getElementById(tabId).classList.add('active');
        
        // Load data for the tab if needed
        if (tabId === 'usage') {
            loadUsageStats();
        } else if (tabId === 'admin') {
            loadModels();
        }
    });
});

// Chat functionality
sendButton.addEventListener('click', sendMessage);
userInput.addEventListener('keydown', (event) => {
    if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        sendMessage();
    }
});

async function sendMessage() {
    const userMessage = userInput.value.trim();
    
    if (!userMessage || isProcessing) return;
    
    isProcessing = true;
    sendButton.disabled = true;
    sendButton.innerHTML = 'Sending <span class="loading"></span>';
    
    // Add user message to chat
    addMessage('user', userMessage);
    userInput.value = '';
    
    // Add to messages array
    messages.push({ role: 'user', content: userMessage });
    
    try {
        // Send request to server
        const response = await fetch('/api/chat', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                model: modelSelect.value,
                messages: messages
            })
        });
        
        const data = await response.json();
        
        if (response.ok) {
            // Add AI message to chat
            addMessage('ai', data.message);
            
            // Add to messages array
            messages.push({ role: 'assistant', content: data.message });
        } else {
            // Show error message
            addMessage('ai', `Error: ${data.error}`);
        }
    } catch (error) {
        console.error('Error:', error);
        addMessage('ai', `Error: ${error.message}`);
    } finally {
        isProcessing = false;
        sendButton.disabled = false;
        sendButton.textContent = 'Send';
    }
}

function addMessage(role, content) {
    const messageElement = document.createElement('div');
    messageElement.classList.add('message');
    messageElement.classList.add(`${role}-message`);
    
    // Replace newlines with <br> tags
    const formattedContent = content.replace(/\n/g, '<br>');
    
    messageElement.innerHTML = formattedContent;
    messagesContainer.appendChild(messageElement);
    
    // Scroll to bottom
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
}

// Usage stats functionality
refreshUsageButton.addEventListener('click', loadUsageStats);

async function loadUsageStats() {
    try {
        totalStatsContainer.innerHTML = '<p>Loading...</p>';
        modelStatsContainer.innerHTML = '<p>Loading...</p>';
        dailyStatsContainer.innerHTML = '<p>Loading...</p>';
        
        const response = await fetch('/api/usage');
        const data = await response.json();
        
        if (response.ok) {
            displayUsageStats(data);
        } else {
            totalStatsContainer.innerHTML = `<p>Error: ${data.error}</p>`;
            modelStatsContainer.innerHTML = '';
            dailyStatsContainer.innerHTML = '';
        }
    } catch (error) {
        console.error('Error:', error);
        totalStatsContainer.innerHTML = `<p>Error: ${error.message}</p>`;
        modelStatsContainer.innerHTML = '';
        dailyStatsContainer.innerHTML = '';
    }
}

function displayUsageStats(data) {
    const usage = data.usage;
    
    // Display total stats
    totalStatsContainer.innerHTML = `
        <table>
            <tr>
                <th>Metric</th>
                <th>Value</th>
            </tr>
            <tr>
                <td>Total Tokens</td>
                <td>${formatNumber(usage.total_tokens)}</td>
            </tr>
            <tr>
                <td>Prompt Tokens</td>
                <td>${formatNumber(usage.total_prompt_tokens)}</td>
            </tr>
            <tr>
                <td>Completion Tokens</td>
                <td>${formatNumber(usage.total_completion_tokens)}</td>
            </tr>
            <tr>
                <td>Total Requests</td>
                <td>${formatNumber(usage.total_requests)}</td>
            </tr>
        </table>
    `;
    
    // Display model stats
    let modelStatsHtml = '<table><tr><th>Model</th><th>Tokens</th></tr>';
    
    if (usage.models) {
        Object.entries(usage.models).forEach(([model, tokens]) => {
            modelStatsHtml += `
                <tr>
                    <td>${model}</td>
                    <td>${formatNumber(tokens)}</td>
                </tr>
            `;
        });
    } else {
        modelStatsHtml += '<tr><td colspan="2">No model data available</td></tr>';
    }
    
    modelStatsHtml += '</table>';
    modelStatsContainer.innerHTML = modelStatsHtml;
    
    // Display daily stats
    let dailyStatsHtml = '<table><tr><th>Date</th><th>Tokens</th><th>Requests</th></tr>';
    
    if (usage.daily) {
        Object.entries(usage.daily).forEach(([date, stats]) => {
            dailyStatsHtml += `
                <tr>
                    <td>${date}</td>
                    <td>${formatNumber(stats.total_tokens)}</td>
                    <td>${formatNumber(stats.requests)}</td>
                </tr>
            `;
        });
    } else {
        dailyStatsHtml += '<tr><td colspan="3">No daily data available</td></tr>';
    }
    
    dailyStatsHtml += '</table>';
    dailyStatsContainer.innerHTML = dailyStatsHtml;
}

// Admin functionality
clearCacheButton.addEventListener('click', clearCache);
refreshModelsButton.addEventListener('click', loadModels);

async function clearCache() {
    try {
        clearCacheButton.disabled = true;
        clearCacheButton.innerHTML = 'Clearing <span class="loading"></span>';
        
        const response = await fetch('/api/cache/clear', {
            method: 'POST'
        });
        
        const data = await response.json();
        
        if (response.ok) {
            cacheStatusContainer.textContent = 'Cache cleared successfully!';
            cacheStatusContainer.className = 'status-message success';
        } else {
            cacheStatusContainer.textContent = `Error: ${data.error}`;
            cacheStatusContainer.className = 'status-message error';
        }
    } catch (error) {
        console.error('Error:', error);
        cacheStatusContainer.textContent = `Error: ${error.message}`;
        cacheStatusContainer.className = 'status-message error';
    } finally {
        clearCacheButton.disabled = false;
        clearCacheButton.textContent = 'Clear Cache';
        
        // Hide status message after 5 seconds
        setTimeout(() => {
            cacheStatusContainer.style.display = 'none';
        }, 5000);
    }
}

async function loadModels() {
    try {
        modelsListContainer.innerHTML = '<p>Loading...</p>';
        
        const response = await fetch('/api/models');
        const data = await response.json();
        
        if (response.ok) {
            displayModels(data);
        } else {
            modelsListContainer.innerHTML = `<p>Error: ${data.error}</p>`;
        }
    } catch (error) {
        console.error('Error:', error);
        modelsListContainer.innerHTML = `<p>Error: ${error.message}</p>`;
    }
}

function displayModels(data) {
    if (!data.models || data.models.length === 0) {
        modelsListContainer.innerHTML = '<p>No models available</p>';
        return;
    }
    
    let modelsHtml = '';
    
    data.models.forEach(model => {
        modelsHtml += `
            <div class="model-item">
                <h4>${model.name}</h4>
                <p>ID: ${model.id}</p>
                <p>Provider: ${model.provider}</p>
            </div>
        `;
    });
    
    modelsListContainer.innerHTML = modelsHtml;
}

// Utility functions
function formatNumber(num) {
    return new Intl.NumberFormat().format(num);
}

// Initialize
loadUsageStats();
loadModels();
