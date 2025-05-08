import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import '@testing-library/jest-dom'
import { renderWithProviders } from './test-utils'
import AIFeaturesPage from '../app/dashboard/ai-features/page'

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
  usePathname: () => '/dashboard/ai-features'
}))

describe('AI Features Page', () => {
  it('renders the AI features page with all sections', () => {
    renderWithProviders(<AIFeaturesPage />)

    // Check for page title
    expect(screen.getByText('AI Features')).toBeInTheDocument()

    // Check for card titles
    expect(screen.getByText('AI Models')).toBeInTheDocument()
    expect(screen.getByText('Usage Statistics')).toBeInTheDocument()
    expect(screen.getByText('Response Cache')).toBeInTheDocument()
    expect(screen.getByText('Prompt Templates')).toBeInTheDocument()
    expect(screen.getByText('Vector Databases')).toBeInTheDocument()
    expect(screen.getByText('LLM Providers')).toBeInTheDocument()
    expect(screen.getByText('Safety & Moderation')).toBeInTheDocument()
    expect(screen.getByText('Model Aggregation')).toBeInTheDocument()
    expect(screen.getByText('Semantic Routing')).toBeInTheDocument()
  })
})
