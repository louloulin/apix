/**
 * Authentication API client
 */
import { ApiClient } from "./base";

export interface User {
  username: string;
  role: string;
}

export interface LoginResponse {
  success: boolean;
  token: string;
  user: User;
}

/**
 * Authentication-specific API client
 */
export class AuthApiClient extends ApiClient {
  /**
   * Login with username and password
   */
  async login(username: string, password: string) {
    return this.post<LoginResponse>('/admin/auth/login', { username, password });
  }

  /**
   * Logout the current user
   */
  async logout() {
    return this.post<{ success: boolean; message: string }>('/admin/auth/logout');
  }

  /**
   * Get the current user
   */
  async getCurrentUser() {
    return this.get<{ user: User }>('/admin/auth/me');
  }

  /**
   * Register a new user
   */
  async register(username: string, password: string) {
    return this.post<{ success: boolean; message: string }>('/admin/auth/register', { username, password });
  }

  /**
   * Change password
   */
  async changePassword(currentPassword: string, newPassword: string) {
    return this.post<{ success: boolean; message: string }>('/admin/auth/change-password', { currentPassword, newPassword });
  }
}

// Create singleton instance
export const authApi = new AuthApiClient();
