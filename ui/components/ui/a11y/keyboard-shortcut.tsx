"use client"

import { useEffect } from "react"
import { cn } from "@/lib/utils"

interface KeyboardShortcutProps {
  keys: string[]
  onTrigger: () => void
  disabled?: boolean
  preventDefault?: boolean
  stopPropagation?: boolean
  className?: string
  children?: React.ReactNode
}

/**
 * KeyboardShortcut component
 * 
 * This component adds keyboard shortcuts to your application.
 * It can be used to trigger actions when specific key combinations are pressed.
 */
export function KeyboardShortcut({
  keys,
  onTrigger,
  disabled = false,
  preventDefault = true,
  stopPropagation = true,
  className,
  children
}: KeyboardShortcutProps) {
  useEffect(() => {
    if (disabled) return
    
    const handleKeyDown = (e: KeyboardEvent) => {
      // Convert keys to lowercase for case-insensitive comparison
      const pressedKey = e.key.toLowerCase()
      
      // Check if all modifier keys match
      const altRequired = keys.includes('alt')
      const ctrlRequired = keys.includes('ctrl') || keys.includes('control')
      const shiftRequired = keys.includes('shift')
      const metaRequired = keys.includes('meta') || keys.includes('cmd') || keys.includes('command')
      
      // Check if the pressed key is one of the required keys
      const keyRequired = keys.some(k => {
        const lowerKey = k.toLowerCase()
        return lowerKey !== 'alt' && 
               lowerKey !== 'ctrl' && 
               lowerKey !== 'control' && 
               lowerKey !== 'shift' && 
               lowerKey !== 'meta' && 
               lowerKey !== 'cmd' && 
               lowerKey !== 'command' && 
               lowerKey === pressedKey
      })
      
      // Check if all conditions are met
      if (
        (altRequired === e.altKey) &&
        (ctrlRequired === e.ctrlKey) &&
        (shiftRequired === e.shiftKey) &&
        (metaRequired === e.metaKey) &&
        keyRequired
      ) {
        if (preventDefault) {
          e.preventDefault()
        }
        
        if (stopPropagation) {
          e.stopPropagation()
        }
        
        onTrigger()
      }
    }
    
    // Add event listener
    document.addEventListener('keydown', handleKeyDown)
    
    // Clean up
    return () => {
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [keys, onTrigger, disabled, preventDefault, stopPropagation])
  
  // If children are provided, render them
  if (children) {
    return (
      <div className={className}>
        {children}
      </div>
    )
  }
  
  // Otherwise, render nothing (just the keyboard shortcut functionality)
  return null
}
