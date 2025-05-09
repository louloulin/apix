"use client"

import { ReactNode, useState } from "react"
import { NavigationMenu, NavigationMenuContent, NavigationMenuItem, NavigationMenuLink, NavigationMenuList, NavigationMenuTrigger } from "../ui/navigation-menu"
import { Button } from "../ui/button"
import { Sheet, SheetContent, SheetTrigger } from "../ui/sheet"
import Link from "next/link"
import { cn } from "@/lib/utils"
import { UserProfile } from './user-profile'
import { ThemeToggle } from '../ui/theme-toggle'
import { LanguageSelector } from '../ui/language-selector'
import { SkipLink } from '../ui/a11y/skip-link'
import { KeyboardShortcut } from '../ui/a11y/keyboard-shortcut'
import { ScreenReaderText } from '../ui/a11y/screen-reader-text'
import { useTranslations, useLocale } from 'next-intl'
import { useRouter } from 'next/navigation'
import {
  Menu,
  X,
  Home,
  BarChart2,
  Settings,
  LogOut,
  HelpCircle,
  Search
} from "lucide-react"

interface NavItem {
  titleKey: string
  href: string
  icon?: ReactNode
  shortcut?: string[]
  children?: NavItem[]
}

export function A11yDashboardLayout({ children }: { children: React.ReactNode }) {
  const t = useTranslations('common')
  const router = useRouter()
  const locale = useLocale()
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false)
  const [isSearchOpen, setIsSearchOpen] = useState(false)

  // Navigation items with translation keys, icons, and keyboard shortcuts
  const navItems: NavItem[] = [
    {
      titleKey: "dashboard",
      href: "/dashboard",
      icon: <Home className="h-4 w-4" />,
      shortcut: ['g', 'd']
    },
    {
      titleKey: "analytics",
      href: "/dashboard/analytics",
      icon: <BarChart2 className="h-4 w-4" />,
      shortcut: ['g', 'a']
    },
    {
      titleKey: "plugins",
      href: "/dashboard/plugins",
      icon: <Settings className="h-4 w-4" />,
      shortcut: ['g', 'p'],
      children: [
        {
          titleKey: "plugins",
          href: "/dashboard/plugins",
          icon: <Settings className="h-4 w-4" />
        },
        {
          titleKey: "plugins",
          href: "/dashboard/plugins-new",
          icon: <Settings className="h-4 w-4" />
        },
      ],
    },
    {
      titleKey: "routes",
      href: "/dashboard/routes",
      icon: <Settings className="h-4 w-4" />,
      shortcut: ['g', 'r']
    },
    {
      titleKey: "configuration",
      href: "/dashboard/config",
      icon: <Settings className="h-4 w-4" />,
      shortcut: ['g', 'c']
    },
    {
      titleKey: "systemMetrics",
      href: "/dashboard/metrics",
      icon: <Settings className="h-4 w-4" />,
      shortcut: ['g', 'm']
    },
    {
      titleKey: "aiManagement",
      href: "/dashboard/ai",
      icon: <Settings className="h-4 w-4" />,
      shortcut: ['g', 'i'],
      children: [
        {
          titleKey: "models",
          href: "/dashboard/ai/models",
          icon: <Settings className="h-4 w-4" />
        },
        {
          titleKey: "routingRules",
          href: "/dashboard/ai/routing",
          icon: <Settings className="h-4 w-4" />
        },
        {
          titleKey: "promptTemplates",
          href: "/dashboard/ai-features/prompts",
          icon: <Settings className="h-4 w-4" />
        },
        {
          titleKey: "vectorDatabases",
          href: "/dashboard/ai-features/vector-db",
          icon: <Settings className="h-4 w-4" />
        },
      ],
    },
  ]

  // Handle keyboard shortcuts
  const handleSearch = () => {
    setIsSearchOpen(true)
  }

  const handleHelp = () => {
    // Open help dialog or navigate to help page
    alert("Help functionality")
  }

  return (
    <div className="flex min-h-screen flex-col">
      {/* Skip link for keyboard users */}
      <SkipLink />

      {/* Keyboard shortcuts */}
      <KeyboardShortcut keys={['/']} onTrigger={handleSearch} />
      <KeyboardShortcut keys={['?']} onTrigger={handleHelp} />
      <KeyboardShortcut keys={['g', 'h']} onTrigger={() => router.push(`/${locale}`)} />

      {/* Add keyboard shortcuts for navigation items */}
      {navItems.map((item, index) => {
        if (item.shortcut) {
          return (
            <KeyboardShortcut
              key={index}
              keys={item.shortcut}
              onTrigger={() => router.push(`/${locale}${item.href}`)}
            />
          )
        }
        return null
      })}

      <header className="sticky top-0 z-40 border-b bg-background" role="banner">
        <div className="container flex h-16 items-center justify-between py-4">
          <div className="flex items-center gap-4">
            <Sheet open={isMobileMenuOpen} onOpenChange={setIsMobileMenuOpen}>
              <SheetTrigger asChild>
                <Button
                  variant="outline"
                  size="icon"
                  className="md:hidden"
                  aria-label={t('common.toggleMenu')}
                >
                  <Menu className="h-5 w-5" />
                </Button>
              </SheetTrigger>
              <SheetContent side="left" className="w-[240px] sm:w-[300px]">
                <div className="flex items-center justify-between pr-4">
                  <Link href={`/${locale}`} className="flex items-center" onClick={() => setIsMobileMenuOpen(false)}>
                    <span className="font-bold">APIX AI Gateway</span>
                  </Link>
                  <Button
                    variant="ghost"
                    size="icon"
                    onClick={() => setIsMobileMenuOpen(false)}
                    aria-label={t('common.closeMenu')}
                  >
                    <X className="h-5 w-5" />
                  </Button>
                </div>
                <nav className="mt-6 flex flex-col gap-4 pr-4" aria-label={t('common.mainNavigation')}>
                  {navItems.map((item, index) => (
                    <div key={index}>
                      {item.children ? (
                        <div className="flex flex-col gap-2">
                          <div className="font-medium">
                            {item.icon && <span className="mr-2">{item.icon}</span>}
                            {t(item.titleKey)}
                            {item.shortcut && (
                              <span className="ml-2 text-xs text-muted-foreground">
                                ({item.shortcut.join('+')})</span>
                            )}
                          </div>
                          <div className="flex flex-col gap-1 pl-4">
                            {item.children.map((child, childIndex) => (
                              <Link
                                key={childIndex}
                                href={`/${locale}${child.href}`}
                                className="text-muted-foreground hover:text-foreground flex items-center"
                                onClick={() => setIsMobileMenuOpen(false)}
                              >
                                {child.icon && <span className="mr-2">{child.icon}</span>}
                                {t(child.titleKey)}
                              </Link>
                            ))}
                          </div>
                        </div>
                      ) : (
                        <Link
                          href={`/${locale}${item.href}`}
                          className="font-medium hover:text-foreground flex items-center"
                          onClick={() => setIsMobileMenuOpen(false)}
                        >
                          {item.icon && <span className="mr-2">{item.icon}</span>}
                          {t(item.titleKey)}
                          {item.shortcut && (
                            <span className="ml-2 text-xs text-muted-foreground">
                              ({item.shortcut.join('+')})</span>
                          )}
                        </Link>
                      )}
                    </div>
                  ))}
                </nav>
              </SheetContent>
            </Sheet>
            <Link href={`/${locale}`} className="flex items-center gap-2">
              <span className="font-bold">APIX AI Gateway</span>
            </Link>
          </div>
          <div className="hidden md:flex">
            <NavigationMenu aria-label={t('common.mainNavigation')}>
              <NavigationMenuList>
                {navItems.map((item, index) => (
                  <NavigationMenuItem key={index}>
                    {item.children ? (
                      <>
                        <NavigationMenuTrigger aria-label={t(item.titleKey)}>
                          <span className="flex items-center">
                            {item.icon && <span className="mr-2">{item.icon}</span>}
                            {t(item.titleKey)}
                          </span>
                        </NavigationMenuTrigger>
                        <NavigationMenuContent>
                          <ul className="grid w-[400px] gap-3 p-4 md:w-[500px] md:grid-cols-2 lg:w-[600px]">
                            {item.children.map((child, childIndex) => (
                              <li key={childIndex}>
                                <NavigationMenuLink asChild>
                                  <Link
                                    href={`/${locale}${child.href}`}
                                    className={cn(
                                      "block select-none space-y-1 rounded-md p-3 leading-none no-underline outline-none transition-colors hover:bg-accent hover:text-accent-foreground focus:bg-accent focus:text-accent-foreground"
                                    )}
                                  >
                                    <div className="text-sm font-medium leading-none flex items-center">
                                      {child.icon && <span className="mr-2">{child.icon}</span>}
                                      {t(child.titleKey)}
                                    </div>
                                  </Link>
                                </NavigationMenuLink>
                              </li>
                            ))}
                          </ul>
                        </NavigationMenuContent>
                      </>
                    ) : (
                      <Link
                        href={`/${locale}${item.href}`}
                        className={cn(
                          "block select-none space-y-1 rounded-md p-3 leading-none no-underline outline-none transition-colors hover:bg-accent hover:text-accent-foreground focus:bg-accent focus:text-accent-foreground"
                        )}
                      >
                        <div className="text-sm font-medium leading-none flex items-center">
                          {item.icon && <span className="mr-2">{item.icon}</span>}
                          {t(item.titleKey)}
                          {item.shortcut && (
                            <ScreenReaderText>
                              (Shortcut: {item.shortcut.join(' then ')})
                            </ScreenReaderText>
                          )}
                        </div>
                      </Link>
                    )}
                  </NavigationMenuItem>
                ))}
              </NavigationMenuList>
            </NavigationMenu>
          </div>
          <div className="flex items-center gap-2">
            <Button
              variant="ghost"
              size="icon"
              onClick={handleSearch}
              aria-label={t('common.search')}
            >
              <Search className="h-5 w-5" />
              <ScreenReaderText>(Press / to search)</ScreenReaderText>
            </Button>
            <Button
              variant="ghost"
              size="icon"
              onClick={handleHelp}
              aria-label={t('common.help')}
            >
              <HelpCircle className="h-5 w-5" />
              <ScreenReaderText>(Press ? for help)</ScreenReaderText>
            </Button>
            <LanguageSelector className="w-[120px]" />
            <ThemeToggle />
            <UserProfile />
          </div>
        </div>
      </header>
      <main id="main-content" className="flex-1 container py-6" role="main">
        {children}
      </main>
      <footer className="border-t py-6 md:py-0" role="contentinfo">
        <div className="container flex flex-col items-center justify-between gap-4 md:h-16 md:flex-row">
          <p className="text-sm text-muted-foreground">
            &copy; {new Date().getFullYear()} APIX AI Gateway. All rights reserved.
          </p>
          <div className="flex items-center gap-4 md:gap-2 md:justify-end">
            <Button variant="ghost" size="sm">
              Terms
            </Button>
            <Button variant="ghost" size="sm">
              Privacy
            </Button>
            <Button variant="ghost" size="sm">
              Contact
            </Button>
          </div>
        </div>
      </footer>
    </div>
  )
}
