/**
 * AI Models API client
 */

import { ApiResponse } from "@/lib/types/api";

// AI Model types
export type ModelProvider = 'openai' | 'anthropic' | 'google' | 'azure' | 'custom';
export type ModelStatus = 'active' | 'inactive' | 'deprecated';

export interface AIModel {
  id: string;
  name: string;
  provider: ModelProvider;
  version: string;
  status: ModelStatus;
  contextWindow: number;
  inputCostPer1kTokens: number;
  outputCostPer1kTokens: number;
  maxTokens: number;
  capabilities: string[];
  supportsFunctions: boolean;
  supportsVision: boolean;
  supportsStreaming: boolean;
}

export interface ModelUsage {
  id: string;
  modelId: string;
  date: string;
  requestCount: number;
  tokenCount: number;
  cost: number;
  averageLatency: number;
}

// API endpoints
const API_BASE = '/api';
const MODELS_ENDPOINT = `${API_BASE}/ai/models`;

/**
 * Get all AI models
 */
export async function getModels(): Promise<ApiResponse<AIModel[]>> {
  try {
    const response = await fetch(MODELS_ENDPOINT);
    
    if (!response.ok) {
      throw new Error(`Failed to fetch models: ${response.statusText}`);
    }
    
    const data = await response.json();
    return {
      success: true,
      data: data.models || []
    };
  } catch (error) {
    console.error('Error fetching models:', error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
}

/**
 * Get model by ID
 */
export async function getModel(id: string): Promise<ApiResponse<AIModel>> {
  try {
    const response = await fetch(`${MODELS_ENDPOINT}/${id}`);
    
    if (!response.ok) {
      throw new Error(`Failed to fetch model: ${response.statusText}`);
    }
    
    const data = await response.json();
    return {
      success: true,
      data: data.model
    };
  } catch (error) {
    console.error(`Error fetching model ${id}:`, error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
}

/**
 * Create a new AI model
 */
export async function createModel(model: Omit<AIModel, 'id'>): Promise<ApiResponse<AIModel>> {
  try {
    const response = await fetch(MODELS_ENDPOINT, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(model)
    });
    
    if (!response.ok) {
      throw new Error(`Failed to create model: ${response.statusText}`);
    }
    
    const data = await response.json();
    return {
      success: true,
      data: data.model
    };
  } catch (error) {
    console.error('Error creating model:', error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
}

/**
 * Update an existing AI model
 */
export async function updateModel(id: string, model: Partial<AIModel>): Promise<ApiResponse<AIModel>> {
  try {
    const response = await fetch(`${MODELS_ENDPOINT}/${id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(model)
    });
    
    if (!response.ok) {
      throw new Error(`Failed to update model: ${response.statusText}`);
    }
    
    const data = await response.json();
    return {
      success: true,
      data: data.model
    };
  } catch (error) {
    console.error(`Error updating model ${id}:`, error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
}

/**
 * Delete an AI model
 */
export async function deleteModel(id: string): Promise<ApiResponse<void>> {
  try {
    const response = await fetch(`${MODELS_ENDPOINT}/${id}`, {
      method: 'DELETE'
    });
    
    if (!response.ok) {
      throw new Error(`Failed to delete model: ${response.statusText}`);
    }
    
    return {
      success: true
    };
  } catch (error) {
    console.error(`Error deleting model ${id}:`, error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
}

/**
 * Get model usage statistics
 */
export async function getModelUsage(
  modelId?: string, 
  startDate?: string, 
  endDate?: string
): Promise<ApiResponse<ModelUsage[]>> {
  try {
    let url = `${API_BASE}/ai/usage`;
    const params = new URLSearchParams();
    
    if (modelId) params.append('modelId', modelId);
    if (startDate) params.append('startDate', startDate);
    if (endDate) params.append('endDate', endDate);
    
    if (params.toString()) {
      url += `?${params.toString()}`;
    }
    
    const response = await fetch(url);
    
    if (!response.ok) {
      throw new Error(`Failed to fetch model usage: ${response.statusText}`);
    }
    
    const data = await response.json();
    return {
      success: true,
      data: data.usage || []
    };
  } catch (error) {
    console.error('Error fetching model usage:', error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
}

// Mock data for development
export const mockModels: AIModel[] = [
  {
    id: "gpt-4",
    name: "GPT-4",
    provider: "openai",
    version: "1.0",
    status: "active",
    contextWindow: 8192,
    inputCostPer1kTokens: 0.03,
    outputCostPer1kTokens: 0.06,
    maxTokens: 4096,
    capabilities: ["text-generation", "code-generation", "reasoning"],
    supportsFunctions: true,
    supportsVision: false,
    supportsStreaming: true
  },
  {
    id: "gpt-4-vision",
    name: "GPT-4 Vision",
    provider: "openai",
    version: "1.0",
    status: "active",
    contextWindow: 8192,
    inputCostPer1kTokens: 0.03,
    outputCostPer1kTokens: 0.06,
    maxTokens: 4096,
    capabilities: ["text-generation", "code-generation", "reasoning", "vision"],
    supportsFunctions: true,
    supportsVision: true,
    supportsStreaming: true
  },
  {
    id: "claude-3-opus",
    name: "Claude 3 Opus",
    provider: "anthropic",
    version: "3.0",
    status: "active",
    contextWindow: 200000,
    inputCostPer1kTokens: 0.015,
    outputCostPer1kTokens: 0.075,
    maxTokens: 4096,
    capabilities: ["text-generation", "code-generation", "reasoning", "vision"],
    supportsFunctions: false,
    supportsVision: true,
    supportsStreaming: true
  },
  {
    id: "gemini-pro",
    name: "Gemini Pro",
    provider: "google",
    version: "1.0",
    status: "active",
    contextWindow: 32768,
    inputCostPer1kTokens: 0.00125,
    outputCostPer1kTokens: 0.00375,
    maxTokens: 8192,
    capabilities: ["text-generation", "code-generation", "reasoning"],
    supportsFunctions: true,
    supportsVision: false,
    supportsStreaming: true
  }
];

export const mockModelUsage: ModelUsage[] = [
  {
    id: "usage-1",
    modelId: "gpt-4",
    date: "2024-05-01",
    requestCount: 1250,
    tokenCount: 3750000,
    cost: 187.5,
    averageLatency: 1200
  },
  {
    id: "usage-2",
    modelId: "gpt-4",
    date: "2024-05-02",
    requestCount: 1350,
    tokenCount: 4050000,
    cost: 202.5,
    averageLatency: 1150
  },
  {
    id: "usage-3",
    modelId: "claude-3-opus",
    date: "2024-05-01",
    requestCount: 850,
    tokenCount: 2550000,
    cost: 127.5,
    averageLatency: 950
  },
  {
    id: "usage-4",
    modelId: "claude-3-opus",
    date: "2024-05-02",
    requestCount: 920,
    tokenCount: 2760000,
    cost: 138,
    averageLatency: 980
  },
  {
    id: "usage-5",
    modelId: "gemini-pro",
    date: "2024-05-01",
    requestCount: 1500,
    tokenCount: 4500000,
    cost: 22.5,
    averageLatency: 850
  },
  {
    id: "usage-6",
    modelId: "gemini-pro",
    date: "2024-05-02",
    requestCount: 1650,
    tokenCount: 4950000,
    cost: 24.75,
    averageLatency: 830
  }
];
