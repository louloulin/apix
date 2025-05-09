"use client"

import { ReactNode } from "react"
import { cn } from "@/lib/utils"

interface ResponsiveGridProps {
  children: ReactNode
  className?: string
  cols?: {
    sm?: number
    md?: number
    lg?: number
    xl?: number
  }
  gap?: string
}

export function ResponsiveGrid({
  children,
  className,
  cols = {
    sm: 1,
    md: 2,
    lg: 3,
    xl: 4
  },
  gap = "gap-4"
}: ResponsiveGridProps) {
  // Generate grid columns classes
  const gridCols = [
    cols.sm && `grid-cols-${cols.sm}`,
    cols.md && `md:grid-cols-${cols.md}`,
    cols.lg && `lg:grid-cols-${cols.lg}`,
    cols.xl && `xl:grid-cols-${cols.xl}`
  ].filter(Boolean).join(" ")
  
  return (
    <div className={cn("grid", gridCols, gap, className)}>
      {children}
    </div>
  )
}
