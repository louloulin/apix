/**
 * API client for the APIX backend
 */

// Export the base API client
export { ApiClient } from './api-client/base';

// Import specific API clients
import { AiApiClient } from './api-client/ai';
import { PluginApiClient } from './api-client/plugins';
import { ConfigApiClient } from './api-client/config';
import { MetricsApiClient } from './api-client/metrics';
import { RoutesApiClient } from './api-client/routes';
import { ServicesApiClient } from './api-client/services';
import { AuthApiClient } from './api-client/auth';
import { AIModelsApiClient } from './api-client/ai-models';

// Create singleton instances
export const aiApi = new AiApiClient();
export const pluginApi = new PluginApiClient();
export const configApi = new ConfigApiClient();
export const metricsApi = new MetricsApiClient();
export const routesApi = new RoutesApiClient();
export const servicesApi = new ServicesApiClient();
export const authApi = new AuthApiClient();
export const aiModelsApi = new AIModelsApiClient();
