"use client"

import { cn } from "@/lib/utils"
import { useTranslations } from "next-intl"

interface SkipLinkProps {
  className?: string
  targetId?: string
}

/**
 * SkipLink component
 * 
 * This component provides a way for keyboard users to skip navigation
 * and go directly to the main content of the page.
 * It's visually hidden until it receives focus.
 */
export function SkipLink({ className, targetId = "main-content" }: SkipLinkProps) {
  const t = useTranslations()
  
  return (
    <a
      href={`#${targetId}`}
      className={cn(
        "sr-only focus:not-sr-only focus:absolute focus:top-4 focus:left-4 focus:z-50 focus:p-4 focus:bg-background focus:border focus:border-primary focus:rounded-md",
        className
      )}
    >
      {t('common.skipToContent')}
    </a>
  )
}
