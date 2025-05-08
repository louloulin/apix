import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import '@testing-library/jest-dom'
import { renderWithProviders } from './test-utils'
import PluginsPage from '../app/dashboard/plugins-new/page'

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
  usePathname: () => '/dashboard/plugins-new'
}))

// Mock the API client
vi.mock('@/lib/api-client', () => ({
  pluginApi: {
    getPlugins: vi.fn().mockResolvedValue({
      plugins: [
        {
          id: 'rate-limiter',
          type: 'security',
          status: 'enabled',
          version: '1.0.0',
          config: {}
        },
        {
          id: 'jwt-auth',
          type: 'authentication',
          status: 'enabled',
          version: '1.1.0',
          config: {}
        },
        {
          id: 'request-transformer',
          type: 'transformation',
          status: 'disabled',
          version: '1.0.0',
          config: {}
        }
      ]
    })
  }
}))

describe('Plugins New Page', () => {
  it('renders the plugins page with correct title and description', () => {
    renderWithProviders(<PluginsPage />)
    
    // Check for page title and description
    expect(screen.getByText('Plugins Management')).toBeInTheDocument()
    expect(screen.getByText('Configure and manage your API Gateway plugins')).toBeInTheDocument()
    
    // Check for Install Plugin button
    expect(screen.getByText('Install Plugin')).toBeInTheDocument()
    
    // Check for tabs
    expect(screen.getByRole('tab', { name: /All Plugins/i })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /Authentication/i })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /Security/i })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /Transformation/i })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /Business Logic/i })).toBeInTheDocument()
  })
})
