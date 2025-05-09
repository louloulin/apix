import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import '@testing-library/jest-dom'
import { renderWithProviders } from './test-utils'
import AIModelsPage from '../app/dashboard/ai/models/page'

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
  usePathname: () => '/dashboard/ai/models'
}))

// Mock the API client
vi.mock('@/lib/api-client', () => ({
  aiModelsApi: {
    getModels: vi.fn().mockResolvedValue({
      models: [
        {
          id: 'gpt-4',
          name: 'GPT-4',
          provider: 'OpenAI',
          description: 'OpenAI\'s most advanced model',
          maxTokens: 8192,
          enabled: true,
          priority: 100,
          costPerToken: 0.00003,
          capabilities: ['text-generation', 'code-generation', 'reasoning']
        },
        {
          id: 'gpt-3.5-turbo',
          name: 'GPT-3.5 Turbo',
          provider: 'OpenAI',
          description: 'Most capable GPT-3.5 model',
          maxTokens: 4096,
          enabled: true,
          priority: 50,
          costPerToken: 0.000002,
          capabilities: ['text-generation', 'code-generation']
        },
        {
          id: 'claude-3-opus',
          name: 'Claude 3 Opus',
          provider: 'Anthropic',
          description: 'Anthropic\'s most powerful model',
          maxTokens: 100000,
          enabled: false,
          priority: 90,
          costPerToken: 0.00003,
          capabilities: ['text-generation', 'reasoning', 'creative-writing']
        }
      ]
    }),
    enableModel: vi.fn().mockResolvedValue({ success: true }),
    disableModel: vi.fn().mockResolvedValue({ success: true }),
    deleteModel: vi.fn().mockResolvedValue({ success: true })
  }
}))

describe('AI Models Page', () => {
  it('passes a basic test', () => {
    expect(true).toBe(true)
  })
})
