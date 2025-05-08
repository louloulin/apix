/**
 * Plugin utility functions
 */

/**
 * Get plugin type display name
 */
export function getPluginTypeDisplay(type: string): string {
  const typeMap: Record<string, string> = {
    'authentication': 'Authentication',
    'auth': 'Authentication',
    'security': 'Security',
    'validation': 'Validation',
    'transformation': 'Transformation',
    'business-logic': 'Business Logic',
    'ai-processing': 'AI Processing',
    'caching': 'Caching',
    'logging': 'Logging',
    'monitoring': 'Monitoring',
  }
  
  return typeMap[type.toLowerCase()] || capitalize(type)
}

/**
 * Get plugin type color
 */
export function getPluginTypeColor(type: string): string {
  const typeMap: Record<string, string> = {
    'authentication': 'blue',
    'auth': 'blue',
    'security': 'red',
    'validation': 'yellow',
    'transformation': 'green',
    'business-logic': 'purple',
    'ai-processing': 'indigo',
    'caching': 'cyan',
    'logging': 'gray',
    'monitoring': 'orange',
  }
  
  return typeMap[type.toLowerCase()] || 'gray'
}

/**
 * Get status color
 */
export function getStatusColor(status: string): string {
  const statusMap: Record<string, string> = {
    'enabled': 'green',
    'disabled': 'gray',
    'error': 'red',
  }
  
  return statusMap[status.toLowerCase()] || 'gray'
}

/**
 * Capitalize the first letter of a string
 */
export function capitalize(str: string): string {
  if (!str) return str
  return str.charAt(0).toUpperCase() + str.slice(1)
}

/**
 * Format JSON for display
 */
export function formatJson(json: any): string {
  return JSON.stringify(json, null, 2)
}

/**
 * Parse JSON safely
 */
export function parseJson(json: string): any {
  try {
    return JSON.parse(json)
  } catch (error) {
    return null
  }
}
