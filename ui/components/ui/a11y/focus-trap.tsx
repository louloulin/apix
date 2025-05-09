"use client"

import { useEffect, useRef } from "react"

interface FocusTrapProps {
  children: React.ReactNode
  active?: boolean
  autoFocus?: boolean
}

/**
 * FocusTrap component
 * 
 * This component traps focus within its children when active.
 * Useful for modals, dialogs, and other components that should
 * keep focus within them when open.
 */
export function FocusTrap({ children, active = true, autoFocus = true }: FocusTrapProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  
  useEffect(() => {
    if (!active) return
    
    const container = containerRef.current
    if (!container) return
    
    // Get all focusable elements
    const focusableElements = container.querySelectorAll<HTMLElement>(
      'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
    )
    
    if (focusableElements.length === 0) return
    
    const firstElement = focusableElements[0]
    const lastElement = focusableElements[focusableElements.length - 1]
    
    // Auto-focus the first focusable element
    if (autoFocus) {
      firstElement.focus()
    }
    
    // Handle tab key to trap focus
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key !== 'Tab') return
      
      // Shift + Tab
      if (e.shiftKey) {
        if (document.activeElement === firstElement) {
          lastElement.focus()
          e.preventDefault()
        }
      } 
      // Tab
      else {
        if (document.activeElement === lastElement) {
          firstElement.focus()
          e.preventDefault()
        }
      }
    }
    
    // Add event listener
    document.addEventListener('keydown', handleKeyDown)
    
    // Clean up
    return () => {
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [active, autoFocus])
  
  return (
    <div ref={containerRef}>
      {children}
    </div>
  )
}
