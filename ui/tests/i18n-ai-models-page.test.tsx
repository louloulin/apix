import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import '@testing-library/jest-dom'
import AIModelsPage from '../app/[locale]/dashboard/ai/models/page'

// Mock next-intl
vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key
}))

// Mock next/navigation
vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn()
  })
}))

// Mock API hooks
vi.mock('@/lib/hooks/use-api-data', () => ({
  useApiData: vi.fn().mockReturnValue({
    data: {
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
          description: 'OpenAI\'s efficient model',
          maxTokens: 4096,
          enabled: true,
          priority: 50,
          costPerToken: 0.000002,
          capabilities: ['text-generation', 'code-generation']
        }
      ]
    },
    isLoading: false,
    error: null,
    refetch: vi.fn(),
    isRefetching: false
  }),
  useApiMutation: vi.fn().mockReturnValue({
    mutate: vi.fn(),
    isLoading: false,
    error: null
  })
}))

// Mock UI components
vi.mock('@/components/layout/i18n-dashboard-layout', () => ({
  I18nDashboardLayout: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="i18n-dashboard-layout">{children}</div>
  )
}))

vi.mock('@/components/ui/data-table', () => ({
  DataTable: ({ columns, data }: { columns: any[], data: any[] }) => (
    <div data-testid="data-table">
      <div>Columns: {columns.length}</div>
      <div>Rows: {data.length}</div>
    </div>
  )
}))

vi.mock('@/components/ui/use-toast', () => ({
  useToast: () => ({
    toast: vi.fn()
  })
}))

describe('Internationalized AI Models Page', () => {
  it('renders the page title correctly', () => {
    render(<AIModelsPage />)
    
    expect(screen.getByText('ai.models.title')).toBeInTheDocument()
    expect(screen.getByText('ai.models.description')).toBeInTheDocument()
  })
  
  it('renders the add model button', () => {
    render(<AIModelsPage />)
    
    expect(screen.getByText('ai.models.addModel')).toBeInTheDocument()
  })
  
  it('renders the data table when data is available', () => {
    render(<AIModelsPage />)
    
    expect(screen.getByTestId('data-table')).toBeInTheDocument()
    expect(screen.getByText('Rows: 2')).toBeInTheDocument()
  })
  
  it('renders the tabs correctly', () => {
    render(<AIModelsPage />)
    
    expect(screen.getByRole('tab', { name: 'All' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'common.enabled' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'common.disabled' })).toBeInTheDocument()
  })
})
