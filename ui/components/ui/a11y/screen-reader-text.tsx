"use client"

import { cn } from "@/lib/utils"

interface ScreenReaderTextProps {
  children: React.ReactNode
  className?: string
}

/**
 * ScreenReaderText component
 * 
 * This component visually hides content but keeps it accessible to screen readers.
 * Use this for content that should be announced to screen readers but not visible on the page.
 */
export function ScreenReaderText({ children, className }: ScreenReaderTextProps) {
  return (
    <span 
      className={cn(
        "absolute w-px h-px p-0 -m-1 overflow-hidden whitespace-nowrap border-0",
        className
      )}
    >
      {children}
    </span>
  )
}
