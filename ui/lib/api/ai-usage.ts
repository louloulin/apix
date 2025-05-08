/**
 * AI Usage Statistics API client
 */

import { ApiResponse } from "@/lib/types/api";

// AI Usage types
export interface UsagePeriod {
  startDate: string;
  endDate: string;
}

export interface TokenUsage {
  input: number;
  output: number;
  total: number;
}

export interface ModelUsageSummary {
  modelId: string;
  modelName: string;
  provider: string;
  requests: number;
  tokens: TokenUsage;
  cost: number;
  averageLatency: number;
}

export interface DailyUsage {
  date: string;
  requests: number;
  tokens: TokenUsage;
  cost: number;
}

export interface UsageSummary {
  period: UsagePeriod;
  totalRequests: number;
  totalTokens: TokenUsage;
  totalCost: number;
  averageLatency: number;
  modelBreakdown: ModelUsageSummary[];
  dailyUsage: DailyUsage[];
}

// API endpoints
const API_BASE = '/api';
const USAGE_ENDPOINT = `${API_BASE}/ai/usage`;

/**
 * Get usage summary for a specific period
 */
export async function getUsageSummary(
  startDate?: string,
  endDate?: string
): Promise<ApiResponse<UsageSummary>> {
  try {
    let url = USAGE_ENDPOINT;
    const params = new URLSearchParams();
    
    if (startDate) params.append('startDate', startDate);
    if (endDate) params.append('endDate', endDate);
    
    if (params.toString()) {
      url += `?${params.toString()}`;
    }
    
    const response = await fetch(url);
    
    if (!response.ok) {
      throw new Error(`Failed to fetch usage summary: ${response.statusText}`);
    }
    
    const data = await response.json();
    return {
      success: true,
      data: data.summary
    };
  } catch (error) {
    console.error('Error fetching usage summary:', error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
}

/**
 * Get usage for a specific model
 */
export async function getModelUsage(
  modelId: string,
  startDate?: string,
  endDate?: string
): Promise<ApiResponse<ModelUsageSummary>> {
  try {
    let url = `${USAGE_ENDPOINT}/models/${modelId}`;
    const params = new URLSearchParams();
    
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
      data: data.usage
    };
  } catch (error) {
    console.error(`Error fetching usage for model ${modelId}:`, error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
}

/**
 * Get daily usage breakdown
 */
export async function getDailyUsage(
  startDate?: string,
  endDate?: string,
  modelId?: string
): Promise<ApiResponse<DailyUsage[]>> {
  try {
    let url = `${USAGE_ENDPOINT}/daily`;
    const params = new URLSearchParams();
    
    if (startDate) params.append('startDate', startDate);
    if (endDate) params.append('endDate', endDate);
    if (modelId) params.append('modelId', modelId);
    
    if (params.toString()) {
      url += `?${params.toString()}`;
    }
    
    const response = await fetch(url);
    
    if (!response.ok) {
      throw new Error(`Failed to fetch daily usage: ${response.statusText}`);
    }
    
    const data = await response.json();
    return {
      success: true,
      data: data.dailyUsage
    };
  } catch (error) {
    console.error('Error fetching daily usage:', error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
}

// Mock data for development
export const mockUsageSummary: UsageSummary = {
  period: {
    startDate: "2024-05-01",
    endDate: "2024-05-07"
  },
  totalRequests: 12500,
  totalTokens: {
    input: 25000000,
    output: 12500000,
    total: 37500000
  },
  totalCost: 1250.75,
  averageLatency: 980,
  modelBreakdown: [
    {
      modelId: "gpt-4",
      modelName: "GPT-4",
      provider: "openai",
      requests: 5000,
      tokens: {
        input: 10000000,
        output: 5000000,
        total: 15000000
      },
      cost: 750.0,
      averageLatency: 1200
    },
    {
      modelId: "claude-3-opus",
      modelName: "Claude 3 Opus",
      provider: "anthropic",
      requests: 3500,
      tokens: {
        input: 7000000,
        output: 3500000,
        total: 10500000
      },
      cost: 412.5,
      averageLatency: 950
    },
    {
      modelId: "gemini-pro",
      modelName: "Gemini Pro",
      provider: "google",
      requests: 4000,
      tokens: {
        input: 8000000,
        output: 4000000,
        total: 12000000
      },
      cost: 88.25,
      averageLatency: 850
    }
  ],
  dailyUsage: [
    {
      date: "2024-05-01",
      requests: 1650,
      tokens: {
        input: 3300000,
        output: 1650000,
        total: 4950000
      },
      cost: 165.25
    },
    {
      date: "2024-05-02",
      requests: 1750,
      tokens: {
        input: 3500000,
        output: 1750000,
        total: 5250000
      },
      cost: 175.5
    },
    {
      date: "2024-05-03",
      requests: 1850,
      tokens: {
        input: 3700000,
        output: 1850000,
        total: 5550000
      },
      cost: 185.75
    },
    {
      date: "2024-05-04",
      requests: 1950,
      tokens: {
        input: 3900000,
        output: 1950000,
        total: 5850000
      },
      cost: 196.0
    },
    {
      date: "2024-05-05",
      requests: 1650,
      tokens: {
        input: 3300000,
        output: 1650000,
        total: 4950000
      },
      cost: 165.25
    },
    {
      date: "2024-05-06",
      requests: 1750,
      tokens: {
        input: 3500000,
        output: 1750000,
        total: 5250000
      },
      cost: 175.5
    },
    {
      date: "2024-05-07",
      requests: 1900,
      tokens: {
        input: 3800000,
        output: 1900000,
        total: 5700000
      },
      cost: 187.5
    }
  ]
};
