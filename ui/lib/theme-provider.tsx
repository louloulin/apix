"use client"

import * as React from "react"
import { ThemeProvider as NextThemesProvider } from "next-themes"

interface ThemeProviderProps {
  children: React.ReactNode
  [key: string]: unknown
}

export function ThemeProvider({ children, ...props }: ThemeProviderProps) {
  console.log('ThemeProvider initializing with props:', props)

  // Log when theme changes
  const onThemeChange = (theme: string) => {
    console.log('Theme changed to:', theme)
  }

  return (
    <NextThemesProvider
      attribute="class"
      defaultTheme="system"
      enableSystem
      enableColorScheme
      disableTransitionOnChange
      storageKey="apix-theme"
      onValueChange={onThemeChange}
      {...props}
    >
      {children}
    </NextThemesProvider>
  )
}