/**
 * Plugins API client
 */

import { ApiResponse } from "@/lib/types/api";

// Plugin types
export type PluginStatus = 'enabled' | 'disabled' | 'error';

export interface Plugin {
  id: string;
  type: string;
  config: Record<string, any>;
  status: PluginStatus;
  version?: string;
}

export interface PluginType {
  id: string;
  name: string;
  description: string;
  configSchema?: Record<string, any>;
  defaultConfig?: Record<string, any>;
}

// API endpoints
const API_BASE = '/api';
const PLUGINS_ENDPOINT = `${API_BASE}/plugins`;

/**
 * Get all plugins
 */
export async function getPlugins(): Promise<ApiResponse<Plugin[]>> {
  try {
    const response = await fetch(PLUGINS_ENDPOINT);
    
    if (!response.ok) {
      throw new Error(`Failed to fetch plugins: ${response.statusText}`);
    }
    
    const data = await response.json();
    return {
      success: true,
      data: data.plugins || []
    };
  } catch (error) {
    console.error('Error fetching plugins:', error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
}

/**
 * Get plugin by ID
 */
export async function getPlugin(id: string): Promise<ApiResponse<Plugin>> {
  try {
    const response = await fetch(`${PLUGINS_ENDPOINT}/${id}`);
    
    if (!response.ok) {
      throw new Error(`Failed to fetch plugin: ${response.statusText}`);
    }
    
    const data = await response.json();
    return {
      success: true,
      data: data.plugin
    };
  } catch (error) {
    console.error(`Error fetching plugin ${id}:`, error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
}

/**
 * Create a new plugin
 */
export async function createPlugin(plugin: Omit<Plugin, 'status'>): Promise<ApiResponse<Plugin>> {
  try {
    const response = await fetch(PLUGINS_ENDPOINT, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(plugin)
    });
    
    if (!response.ok) {
      throw new Error(`Failed to create plugin: ${response.statusText}`);
    }
    
    const data = await response.json();
    return {
      success: true,
      data: data.plugin
    };
  } catch (error) {
    console.error('Error creating plugin:', error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
}

/**
 * Update an existing plugin
 */
export async function updatePlugin(id: string, plugin: Partial<Plugin>): Promise<ApiResponse<Plugin>> {
  try {
    const response = await fetch(`${PLUGINS_ENDPOINT}/${id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(plugin)
    });
    
    if (!response.ok) {
      throw new Error(`Failed to update plugin: ${response.statusText}`);
    }
    
    const data = await response.json();
    return {
      success: true,
      data: data.plugin
    };
  } catch (error) {
    console.error(`Error updating plugin ${id}:`, error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
}

/**
 * Delete a plugin
 */
export async function deletePlugin(id: string): Promise<ApiResponse<void>> {
  try {
    const response = await fetch(`${PLUGINS_ENDPOINT}/${id}`, {
      method: 'DELETE'
    });
    
    if (!response.ok) {
      throw new Error(`Failed to delete plugin: ${response.statusText}`);
    }
    
    return {
      success: true
    };
  } catch (error) {
    console.error(`Error deleting plugin ${id}:`, error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
}

/**
 * Enable a plugin
 */
export async function enablePlugin(id: string): Promise<ApiResponse<void>> {
  try {
    const response = await fetch(`${PLUGINS_ENDPOINT}/${id}/enable`, {
      method: 'POST'
    });
    
    if (!response.ok) {
      throw new Error(`Failed to enable plugin: ${response.statusText}`);
    }
    
    return {
      success: true
    };
  } catch (error) {
    console.error(`Error enabling plugin ${id}:`, error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
}

/**
 * Disable a plugin
 */
export async function disablePlugin(id: string): Promise<ApiResponse<void>> {
  try {
    const response = await fetch(`${PLUGINS_ENDPOINT}/${id}/disable`, {
      method: 'POST'
    });
    
    if (!response.ok) {
      throw new Error(`Failed to disable plugin: ${response.statusText}`);
    }
    
    return {
      success: true
    };
  } catch (error) {
    console.error(`Error disabling plugin ${id}:`, error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
}

/**
 * Get available plugin types
 */
export async function getPluginTypes(): Promise<ApiResponse<PluginType[]>> {
  try {
    const response = await fetch(`${PLUGINS_ENDPOINT}/types`);
    
    if (!response.ok) {
      throw new Error(`Failed to fetch plugin types: ${response.statusText}`);
    }
    
    const data = await response.json();
    return {
      success: true,
      data: data.types || []
    };
  } catch (error) {
    console.error('Error fetching plugin types:', error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
}

/**
 * Reload a plugin
 */
export async function reloadPlugin(id: string): Promise<ApiResponse<void>> {
  try {
    const response = await fetch(`${PLUGINS_ENDPOINT}/${id}/reload`, {
      method: 'POST'
    });
    
    if (!response.ok) {
      throw new Error(`Failed to reload plugin: ${response.statusText}`);
    }
    
    return {
      success: true
    };
  } catch (error) {
    console.error(`Error reloading plugin ${id}:`, error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'Unknown error'
    };
  }
}
