/**
 * AI Cache Management API client
 */

import { ApiResponse } from "@/lib/types/api";

// AI Cache types
export type CacheStrategy = 'exact-match' | 'semantic-similarity' | 'hybrid';

export interface CacheSettings {
  enabled: boolean;
  ttl: number; // Time to live in seconds
  maxSize: number; // Maximum cache size in MB
  strategy: CacheStrategy;
  similarityThreshold: number; // 0.0 to 1.0, only used for semantic-similarity and hybrid strategies
  excludedModels: string[]; // Model IDs to exclude from caching
  excludedPromptPatterns: string[]; // Regex patterns for prompts to exclude from caching
}

export interface CacheStats {
  hitCount: number;
  missCount: number;
  hitRate: number; // 0.0 to 1.0
  size: number; // Current size in MB
  entryCount: number;
  averageSavingsMs: number; // Average time saved in milliseconds
  totalSavingsMs: number; // Total time saved in milliseconds
}

// API endpoints
const API_BASE = '/api';
const CACHE_ENDPOINT = `${API_BASE}/ai/cache`;

/**
 * Get cache settings
 */
export async function getCacheSettings(): Promise<ApiResponse<CacheSettings>> {
  try {
    const response = await fetch(CACHE_ENDPOINT);
    
    if (!response.ok) {
      throw new Error(`Failed to fetch cache settings: ${response.statusText}`);
    }
    
    const data = await response.json();
    return {
      success: true,
      data: data.settings
    };
  } catch (error) {
    console.error('Error fetching cache settings:', error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
}

/**
 * Update cache settings
 */
export async function updateCacheSettings(settings: Partial<CacheSettings>): Promise<ApiResponse<CacheSettings>> {
  try {
    const response = await fetch(CACHE_ENDPOINT, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(settings)
    });
    
    if (!response.ok) {
      throw new Error(`Failed to update cache settings: ${response.statusText}`);
    }
    
    const data = await response.json();
    return {
      success: true,
      data: data.settings
    };
  } catch (error) {
    console.error('Error updating cache settings:', error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
}

/**
 * Get cache statistics
 */
export async function getCacheStats(): Promise<ApiResponse<CacheStats>> {
  try {
    const response = await fetch(`${CACHE_ENDPOINT}/stats`);
    
    if (!response.ok) {
      throw new Error(`Failed to fetch cache stats: ${response.statusText}`);
    }
    
    const data = await response.json();
    return {
      success: true,
      data: data.stats
    };
  } catch (error) {
    console.error('Error fetching cache stats:', error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
}

/**
 * Clear the cache
 */
export async function clearCache(): Promise<ApiResponse<void>> {
  try {
    const response = await fetch(`${CACHE_ENDPOINT}/clear`, {
      method: 'POST'
    });
    
    if (!response.ok) {
      throw new Error(`Failed to clear cache: ${response.statusText}`);
    }
    
    return {
      success: true
    };
  } catch (error) {
    console.error('Error clearing cache:', error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
}

// Mock data for development
export const mockCacheSettings: CacheSettings = {
  enabled: true,
  ttl: 3600, // 1 hour
  maxSize: 1024, // 1 GB
  strategy: 'hybrid',
  similarityThreshold: 0.85,
  excludedModels: ["gpt-4-vision"],
  excludedPromptPatterns: ["^\\s*generate\\s+image\\s*", "^\\s*create\\s+image\\s*"]
};

export const mockCacheStats: CacheStats = {
  hitCount: 12500,
  missCount: 7500,
  hitRate: 0.625, // 62.5%
  size: 512, // 512 MB
  entryCount: 25000,
  averageSavingsMs: 850,
  totalSavingsMs: 10625000 // ~3 hours
};
