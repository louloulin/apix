"use client"

import { ReactNode, useEffect, useState } from "react"
import { cn } from "@/lib/utils"

interface ResponsiveContainerProps {
  children: ReactNode
  className?: string
  breakpoints?: {
    sm?: string
    md?: string
    lg?: string
    xl?: string
  }
}

export function ResponsiveContainer({
  children,
  className,
  breakpoints = {
    sm: "max-w-full px-4",
    md: "max-w-3xl px-6",
    lg: "max-w-5xl px-8",
    xl: "max-w-7xl px-10"
  }
}: ResponsiveContainerProps) {
  const [screenSize, setScreenSize] = useState<"sm" | "md" | "lg" | "xl">("sm")
  
  useEffect(() => {
    // Function to update screen size
    const updateScreenSize = () => {
      const width = window.innerWidth
      
      if (width < 640) {
        setScreenSize("sm")
      } else if (width < 768) {
        setScreenSize("md")
      } else if (width < 1024) {
        setScreenSize("lg")
      } else {
        setScreenSize("xl")
      }
    }
    
    // Initial update
    updateScreenSize()
    
    // Add event listener
    window.addEventListener("resize", updateScreenSize)
    
    // Clean up
    return () => window.removeEventListener("resize", updateScreenSize)
  }, [])
  
  // Get the appropriate class based on screen size
  const getBreakpointClass = () => {
    switch (screenSize) {
      case "sm":
        return breakpoints.sm
      case "md":
        return breakpoints.md
      case "lg":
        return breakpoints.lg
      case "xl":
        return breakpoints.xl
      default:
        return breakpoints.sm
    }
  }
  
  return (
    <div className={cn("mx-auto", getBreakpointClass(), className)}>
      {children}
    </div>
  )
}
