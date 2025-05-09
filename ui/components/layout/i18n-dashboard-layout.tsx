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
import { useTranslations, useLocale } from 'next-intl'
import { usePathname } from 'next/navigation'

interface NavItem {
  titleKey: string
  href: string
  icon?: ReactNode
  children?: NavItem[]
}

export function I18nDashboardLayout({ children }: { children: React.ReactNode }) {
  const t = useTranslations('common')
  const locale = useLocale()
  const pathname = usePathname()

  // Navigation items with translation keys
  const navItems: NavItem[] = [
    {
      titleKey: "dashboard",
      href: "/dashboard",
    },
    {
      titleKey: "analytics",
      href: "/dashboard/analytics",
    },
    {
      titleKey: "plugins",
      href: "/dashboard/plugins",
      children: [
        {
          titleKey: "plugins",
          href: "/dashboard/plugins",
        },
        {
          titleKey: "plugins",
          href: "/dashboard/plugins-new",
        },
      ],
    },
    {
      titleKey: "routes",
      href: "/dashboard/routes",
    },
    {
      titleKey: "configuration",
      href: "/dashboard/config",
    },
    {
      titleKey: "systemMetrics",
      href: "/dashboard/metrics",
    },
    {
      titleKey: "aiManagement",
      href: "/dashboard/ai",
      children: [
        {
          titleKey: "models",
          href: "/dashboard/ai/models",
        },
        {
          titleKey: "routingRules",
          href: "/dashboard/ai/routing",
        },
        {
          titleKey: "promptTemplates",
          href: "/dashboard/ai-features/prompts",
        },
        {
          titleKey: "vectorDatabases",
          href: "/dashboard/ai-features/vector-db",
        },
      ],
    },
  ]

  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false)

  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-40 border-b bg-background">
        <div className="container flex h-16 items-center justify-between py-4">
          <div className="flex items-center gap-4">
            <Sheet open={isMobileMenuOpen} onOpenChange={setIsMobileMenuOpen}>
              <SheetTrigger asChild>
                <Button variant="outline" size="icon" className="md:hidden">
                  <svg
                    xmlns="http://www.w3.org/2000/svg"
                    width="24"
                    height="24"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    className="h-5 w-5"
                  >
                    <line x1="3" y1="12" x2="21" y2="12" />
                    <line x1="3" y1="6" x2="21" y2="6" />
                    <line x1="3" y1="18" x2="21" y2="18" />
                  </svg>
                  <span className="sr-only">Toggle Menu</span>
                </Button>
              </SheetTrigger>
              <SheetContent side="left" className="w-[240px] sm:w-[300px]">
                <nav className="flex flex-col gap-4 py-4">
                  {navItems.map((item, index) => (
                    <div key={index}>
                      {item.children ? (
                        <div className="flex flex-col gap-2">
                          <div className="font-medium">{t(item.titleKey)}</div>
                          <div className="flex flex-col gap-1 pl-4">
                            {item.children.map((child, childIndex) => (
                              <Link
                                key={childIndex}
                                href={`/${locale}${child.href}`}
                                className="text-muted-foreground hover:text-foreground"
                                onClick={() => setIsMobileMenuOpen(false)}
                              >
                                {t(child.titleKey)}
                              </Link>
                            ))}
                          </div>
                        </div>
                      ) : (
                        <Link
                          href={`/${locale}${item.href}`}
                          className="font-medium hover:text-foreground"
                          onClick={() => setIsMobileMenuOpen(false)}
                        >
                          {t(item.titleKey)}
                        </Link>
                      )}
                    </div>
                  ))}
                </nav>
              </SheetContent>
            </Sheet>
            <Link href={`/${locale}/dashboard`} className="flex items-center gap-2">
              <span className="font-bold">APIX AI Gateway</span>
            </Link>
          </div>
          <div className="hidden md:flex">
            <NavigationMenu>
              <NavigationMenuList>
                {navItems.map((item, index) => (
                  <NavigationMenuItem key={index}>
                    {item.children ? (
                      <>
                        <NavigationMenuTrigger>{t(item.titleKey)}</NavigationMenuTrigger>
                        <NavigationMenuContent>
                          <ul className="grid w-[400px] gap-3 p-4 md:w-[500px] md:grid-cols-2 lg:w-[600px]">
                            {item.children.map((child, childIndex) => (
                              <li key={childIndex}>
                                <NavigationMenuLink asChild>
                                  <Link
                                    href={child.href}
                                    className={cn(
                                      "block select-none space-y-1 rounded-md p-3 leading-none no-underline outline-none transition-colors hover:bg-accent hover:text-accent-foreground focus:bg-accent focus:text-accent-foreground"
                                    )}
                                  >
                                    <div className="text-sm font-medium leading-none">
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
                        <div className="text-sm font-medium leading-none">
                          {t(item.titleKey)}
                        </div>
                      </Link>
                    )}
                  </NavigationMenuItem>
                ))}
              </NavigationMenuList>
            </NavigationMenu>
          </div>
          <div className="flex items-center gap-2">
            <LanguageSelector className="w-[120px]" />
            <ThemeToggle />
            <UserProfile />
          </div>
        </div>
      </header>
      <main className="flex-1 container py-6">{children}</main>
    </div>
  )
}
