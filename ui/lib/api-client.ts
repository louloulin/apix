/**
 * API client for the APIX backend
 */

// Export the base API client
export { ApiClient } from './api-client/base';

// Import specific API clients
import { AiApiClient } from './api-client/ai';
import { PluginApiClient } from './api-client/plugins';

// Create singleton instances
export const aiApi = new AiApiClient();
export const pluginApi = new PluginApiClient();
