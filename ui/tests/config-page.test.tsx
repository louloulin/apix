import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import '@testing-library/jest-dom'
import { renderWithProviders } from './test-utils'
import ConfigPage from '../app/dashboard/config/page'

// Mock the next/navigation hooks
vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn(),
    prefetch: vi.fn()
  }),
  useSearchParams: () => ({
    get: vi.fn()
  }),
  usePathname: () => '/dashboard/config'
}))

// Mock the API client
vi.mock('@/lib/api-client', () => ({
  configApi: {
    getConfig: vi.fn().mockResolvedValue({
      server: {
        port: 8080,
        host: "0.0.0.0",
        workerPoolSize: 20,
        compressionSupported: true,
        compressionLevel: 6
      },
      http: {
        idleTimeout: 30,
        connectTimeout: 60,
        keepAlive: true,
        maxPoolSize: 10,
        maxWaitQueueSize: 1000,
        pipelining: false
      },
      security: {
        corsEnabled: true,
        corsAllowedOrigins: "*",
        corsAllowedMethods: "GET,POST,PUT,DELETE,OPTIONS",
        rateLimitingEnabled: true,
        rateLimitRequests: 100,
        rateLimitPeriod: 60
      },
      cache: {
        enabled: true,
        maxSize: 1000,
        ttl: 300,
        cleanupInterval: 60
      }
    })
  }
}))

describe('Gateway Configuration Page', () => {
  it('renders the configuration page with correct title and tabs', () => {
    renderWithProviders(<ConfigPage />)
    
    // Check for page title and description
    expect(screen.getByText('Gateway Configuration')).toBeInTheDocument()
    expect(screen.getByText('Configure the core settings of your API Gateway')).toBeInTheDocument()
    
    // Check for Save Configuration button
    expect(screen.getByText('Save Configuration')).toBeInTheDocument()
    
    // Check for tabs
    expect(screen.getByRole('tab', { name: /Server/i })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /HTTP/i })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /Security/i })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /Cache/i })).toBeInTheDocument()
  })
})
