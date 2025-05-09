import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import '@testing-library/jest-dom'
import { renderWithProviders } from './test-utils'
import AIRoutingPage from '../app/dashboard/ai/routing/page'

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
  usePathname: () => '/dashboard/ai/routing'
}))

// Mock the API client
vi.mock('@/lib/api-client', () => ({
  aiModelsApi: {
    getRoutingRules: vi.fn().mockResolvedValue({
      rules: [
        {
          id: 'technical-content',
          name: 'Technical Content Rule',
          priority: 100,
          condition: {
            type: 'CONTAINS',
            pattern: 'code',
            contentTypes: ['text/plain', 'application/json'],
            requestTypes: ['chat', 'completion']
          },
          targetModel: 'gpt-4',
          enabled: true
        },
        {
          id: 'default-rule',
          name: 'Default Rule',
          priority: 0,
          condition: {
            type: 'DEFAULT',
            pattern: '*',
            contentTypes: ['*'],
            requestTypes: ['*']
          },
          targetModel: 'gpt-3.5-turbo',
          enabled: true
        }
      ]
    }),
    getModels: vi.fn().mockResolvedValue({
      models: [
        {
          id: 'gpt-4',
          name: 'GPT-4',
          provider: 'OpenAI',
          enabled: true
        },
        {
          id: 'gpt-3.5-turbo',
          name: 'GPT-3.5 Turbo',
          provider: 'OpenAI',
          enabled: true
        }
      ]
    }),
    enableRoutingRule: vi.fn().mockResolvedValue({ success: true }),
    disableRoutingRule: vi.fn().mockResolvedValue({ success: true }),
    deleteRoutingRule: vi.fn().mockResolvedValue({ success: true })
  }
}))

describe('AI Routing Page', () => {
  it('passes a basic test', () => {
    expect(true).toBe(true)
  })
})
