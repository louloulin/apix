import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import '@testing-library/jest-dom'
import { ResponsiveContainer } from '../components/layout/responsive-container'
import { ResponsiveGrid } from '../components/layout/responsive-grid'
import { MobileNav } from '../components/layout/mobile-nav'

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
  usePathname: () => '/'
}))

// Mock window.innerWidth for testing responsive behavior
const mockWindowInnerWidth = (width: number) => {
  Object.defineProperty(window, 'innerWidth', {
    writable: true,
    configurable: true,
    value: width
  })
  
  // Trigger resize event
  window.dispatchEvent(new Event('resize'))
}

describe('Responsive Components', () => {
  describe('ResponsiveContainer', () => {
    it('renders children correctly', () => {
      render(
        <ResponsiveContainer>
          <div data-testid="test-child">Test Content</div>
        </ResponsiveContainer>
      )
      
      expect(screen.getByTestId('test-child')).toBeInTheDocument()
      expect(screen.getByText('Test Content')).toBeInTheDocument()
    })
    
    it('applies custom className', () => {
      const { container } = render(
        <ResponsiveContainer className="test-class">
          <div>Test Content</div>
        </ResponsiveContainer>
      )
      
      expect(container.firstChild).toHaveClass('test-class')
    })
  })
  
  describe('ResponsiveGrid', () => {
    it('renders children correctly', () => {
      render(
        <ResponsiveGrid>
          <div data-testid="grid-item-1">Item 1</div>
          <div data-testid="grid-item-2">Item 2</div>
        </ResponsiveGrid>
      )
      
      expect(screen.getByTestId('grid-item-1')).toBeInTheDocument()
      expect(screen.getByTestId('grid-item-2')).toBeInTheDocument()
    })
    
    it('applies grid classes correctly', () => {
      const { container } = render(
        <ResponsiveGrid cols={{ sm: 1, md: 2, lg: 3 }} gap="gap-6">
          <div>Item 1</div>
          <div>Item 2</div>
        </ResponsiveGrid>
      )
      
      expect(container.firstChild).toHaveClass('grid')
      expect(container.firstChild).toHaveClass('grid-cols-1')
      expect(container.firstChild).toHaveClass('md:grid-cols-2')
      expect(container.firstChild).toHaveClass('lg:grid-cols-3')
      expect(container.firstChild).toHaveClass('gap-6')
    })
  })
  
  describe('MobileNav', () => {
    const navItems = [
      {
        title: "Home",
        href: "/"
      },
      {
        title: "Dashboard",
        href: "/dashboard"
      },
      {
        title: "Settings",
        href: "/settings",
        children: [
          {
            title: "Profile",
            href: "/settings/profile"
          },
          {
            title: "Account",
            href: "/settings/account"
          }
        ]
      }
    ]
    
    it('renders the mobile navigation button', () => {
      render(<MobileNav items={navItems} />)
      
      expect(screen.getByRole('button')).toBeInTheDocument()
      expect(screen.getByText('Toggle menu')).toBeInTheDocument()
    })
  })
})
