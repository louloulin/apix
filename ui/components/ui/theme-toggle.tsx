"use client"

import * as React from "react"
import { Moon, Sun } from "lucide-react"
import { useTheme } from "next-themes"

import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { useEffect, useState } from "react"

export function ThemeToggle() {
  const { setTheme, theme, resolvedTheme } = useTheme()
  const [mounted, setMounted] = useState(false)

  // Avoid hydration mismatch by only rendering after component is mounted
  useEffect(() => {
    setMounted(true)
    console.log("Theme toggle mounted, current theme:", theme, "resolved theme:", resolvedTheme)
  }, [theme, resolvedTheme])

  // Simple toggle function that directly switches between light and dark
  const toggleTheme = () => {
    const newTheme = resolvedTheme === 'dark' ? 'light' : 'dark'
    console.log(`Toggling theme from ${resolvedTheme} to ${newTheme}`)
    try {
      setTheme(newTheme)
      console.log(`Theme set to ${newTheme}`)
    } catch (error) {
      console.error('Error setting theme:', error)
    }
  }

  const handleSetTheme = (newTheme: string) => {
    console.log(`Attempting to set theme to ${newTheme}, current theme: ${theme}, resolved theme: ${resolvedTheme}`)
    try {
      setTheme(newTheme)
      console.log(`Theme set to ${newTheme}`)
    } catch (error) {
      console.error('Error setting theme:', error)
    }
  }

  if (!mounted) {
    // 在客户端渲染前，返回一个占位按钮
    return (
      <Button variant="outline" size="icon" className="w-9 h-9 rounded-full">
        <span className="sr-only">Toggle theme</span>
      </Button>
    )
  }

  const currentTheme = resolvedTheme || theme

  return (
    <div className="flex items-center gap-2">
      {/* Simple toggle button */}
      <Button
        variant="outline"
        size="icon"
        className={`w-9 h-9 rounded-full transition-all ${
          currentTheme === 'dark' ? 'bg-slate-800 text-slate-100' : 'bg-slate-100 text-slate-800'
        }`}
        onClick={toggleTheme}
      >
        {currentTheme === "dark" ? (
          <Moon className="h-[1.2rem] w-[1.2rem]" />
        ) : (
          <Sun className="h-[1.2rem] w-[1.2rem]" />
        )}
        <span className="sr-only">Toggle theme</span>
      </Button>

      {/* Dropdown for more options */}
      <DropdownMenu>
        <DropdownMenuTrigger asChild className="hidden md:flex">
          <Button
            variant="outline"
            size="icon"
            className="w-9 h-9 rounded-full hidden"
          >
            <span className="sr-only">Theme options</span>
          </Button>
        </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuItem
          onClick={() => handleSetTheme("light")}
          className={currentTheme === "light" ? "bg-accent font-medium" : ""}
        >
          <Sun className="h-4 w-4 mr-2" />
          Light
        </DropdownMenuItem>
        <DropdownMenuItem
          onClick={() => handleSetTheme("dark")}
          className={currentTheme === "dark" ? "bg-accent font-medium" : ""}
        >
          <Moon className="h-4 w-4 mr-2" />
          Dark
        </DropdownMenuItem>
        <DropdownMenuItem
          onClick={() => handleSetTheme("system")}
          className={currentTheme === "system" ? "bg-accent font-medium" : ""}
        >
          <span className="h-4 w-4 mr-2 flex items-center justify-center">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <rect width="20" height="14" x="2" y="3" rx="2" />
              <line x1="8" x2="16" y1="21" y2="21" />
              <line x1="12" x2="12" y1="17" y2="21" />
            </svg>
          </span>
          System
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
    </div>
  )
}