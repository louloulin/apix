import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import '@testing-library/jest-dom'
import { ScreenReaderText } from '../components/ui/a11y/screen-reader-text'
import { SkipLink } from '../components/ui/a11y/skip-link'
import { KeyboardShortcut } from '../components/ui/a11y/keyboard-shortcut'

// Mock next-intl
vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key === 'common.skipToContent' ? 'Skip to content' : key
}))

describe('Accessibility Components', () => {
  describe('ScreenReaderText', () => {
    it('renders children correctly', () => {
      render(<ScreenReaderText>Test content</ScreenReaderText>)
      
      // The component should render the content
      expect(screen.getByText('Test content')).toBeInTheDocument()
    })
    
    it('applies correct styling for screen readers', () => {
      const { container } = render(<ScreenReaderText>Test content</ScreenReaderText>)
      
      // The component should have the sr-only class
      expect(container.firstChild).toHaveClass('absolute')
      expect(container.firstChild).toHaveClass('w-px')
      expect(container.firstChild).toHaveClass('h-px')
      expect(container.firstChild).toHaveClass('overflow-hidden')
    })
    
    it('applies custom className', () => {
      const { container } = render(<ScreenReaderText className="test-class">Test content</ScreenReaderText>)
      
      // The component should have the custom class
      expect(container.firstChild).toHaveClass('test-class')
    })
  })
  
  describe('SkipLink', () => {
    it('renders correctly', () => {
      render(<SkipLink />)
      
      // The component should render a link with the correct text
      expect(screen.getByText('Skip to content')).toBeInTheDocument()
    })
    
    it('links to the correct target', () => {
      render(<SkipLink targetId="test-target" />)
      
      // The link should point to the correct target
      expect(screen.getByText('Skip to content')).toHaveAttribute('href', '#test-target')
    })
    
    it('applies custom className', () => {
      const { container } = render(<SkipLink className="test-class" />)
      
      // The component should have the custom class
      expect(container.firstChild).toHaveClass('test-class')
    })
  })
  
  describe('KeyboardShortcut', () => {
    it('renders children correctly', () => {
      render(
        <KeyboardShortcut keys={['/']} onTrigger={() => {}}>
          <div>Test content</div>
        </KeyboardShortcut>
      )
      
      // The component should render the content
      expect(screen.getByText('Test content')).toBeInTheDocument()
    })
    
    it('applies custom className', () => {
      const { container } = render(
        <KeyboardShortcut keys={['/']} onTrigger={() => {}} className="test-class">
          <div>Test content</div>
        </KeyboardShortcut>
      )
      
      // The component should have the custom class
      expect(container.firstChild).toHaveClass('test-class')
    })
  })
})
