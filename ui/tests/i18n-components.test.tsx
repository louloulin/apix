import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import '@testing-library/jest-dom'
import { LanguageSelector } from '../components/ui/language-selector'

// Mock next-intl
vi.mock('next-intl', () => ({
  useLocale: () => 'en',
  useTranslations: () => (key: string) => key
}))

// Mock next/navigation
vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: vi.fn(),
    replace: vi.fn()
  }),
  usePathname: () => '/en/dashboard'
}))

describe('Internationalization Components', () => {
  describe('LanguageSelector', () => {
    it('renders correctly', () => {
      render(<LanguageSelector />)
      
      // The component should render a select element
      expect(screen.getByRole('combobox')).toBeInTheDocument()
    })
    
    it('applies custom className', () => {
      const { container } = render(<LanguageSelector className="test-class" />)
      
      // The component should have the custom class
      expect(container.firstChild).toHaveClass('test-class')
    })
  })
})
