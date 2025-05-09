import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import '@testing-library/jest-dom'
import { EnhancedAIUsageChart } from '../components/dashboard/enhanced-ai-usage-chart'

// Mock next-intl
vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key
}))

// Mock recharts
vi.mock('recharts', () => {
  const OriginalModule = vi.importActual('recharts')
  return {
    ...OriginalModule,
    ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
      <div data-testid="responsive-container">{children}</div>
    ),
    AreaChart: ({ children }: { children: React.ReactNode }) => (
      <div data-testid="area-chart">{children}</div>
    ),
    BarChart: ({ children }: { children: React.ReactNode }) => (
      <div data-testid="bar-chart">{children}</div>
    ),
    PieChart: ({ children }: { children: React.ReactNode }) => (
      <div data-testid="pie-chart">{children}</div>
    ),
    Area: () => <div data-testid="area" />,
    Bar: () => <div data-testid="bar" />,
    Pie: () => <div data-testid="pie" />,
    XAxis: () => <div data-testid="x-axis" />,
    YAxis: () => <div data-testid="y-axis" />,
    CartesianGrid: () => <div data-testid="cartesian-grid" />,
    Tooltip: () => <div data-testid="tooltip" />,
    Legend: () => <div data-testid="legend" />,
    Cell: () => <div data-testid="cell" />
  }
})

// Mock data
const mockData = [
  {
    date: "2023-06-01",
    requests: 1200,
    tokens: 240000,
    cost: 4.8,
    models: {
      "gpt-4": { requests: 300, tokens: 90000, cost: 2.7 },
      "gpt-3.5-turbo": { requests: 900, tokens: 150000, cost: 2.1 }
    }
  },
  {
    date: "2023-06-02",
    requests: 1350,
    tokens: 270000,
    cost: 5.4,
    models: {
      "gpt-4": { requests: 350, tokens: 105000, cost: 3.15 },
      "gpt-3.5-turbo": { requests: 1000, tokens: 165000, cost: 2.25 }
    }
  }
]

describe('EnhancedAIUsageChart', () => {
  it('renders loading state correctly', () => {
    render(<EnhancedAIUsageChart isLoading={true} />)
    
    expect(screen.getByText('loading')).toBeInTheDocument()
    expect(screen.getByRole('status')).toBeInTheDocument()
  })
  
  it('renders error state correctly', () => {
    render(<EnhancedAIUsageChart error={new Error('Test error')} />)
    
    expect(screen.getByText('Test error')).toBeInTheDocument()
    expect(screen.getByText('Failed to load AI usage data')).toBeInTheDocument()
  })
  
  it('renders empty state correctly', () => {
    render(<EnhancedAIUsageChart data={[]} />)
    
    expect(screen.getByText('No AI usage data available')).toBeInTheDocument()
  })
  
  it('renders area chart by default', () => {
    render(<EnhancedAIUsageChart data={mockData} />)
    
    expect(screen.getByTestId('area-chart')).toBeInTheDocument()
    expect(screen.getByTestId('responsive-container')).toBeInTheDocument()
  })
  
  it('applies custom className', () => {
    const { container } = render(<EnhancedAIUsageChart data={mockData} className="test-class" />)
    
    expect(container.firstChild).toHaveClass('test-class')
  })
})
