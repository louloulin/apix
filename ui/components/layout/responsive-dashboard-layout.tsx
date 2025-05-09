"use client"

import { ReactNode } from "react"
import { NavigationMenu, NavigationMenuContent, NavigationMenuItem, NavigationMenuLink, NavigationMenuList, NavigationMenuTrigger } from "../ui/navigation-menu"
import { Button } from "../ui/button"
import Link from "next/link"
import { cn } from "@/lib/utils"
import { UserProfile } from './user-profile'
import { ThemeToggle } from '../ui/theme-toggle'
import { MobileNav } from './mobile-nav'
import { ResponsiveContainer } from './responsive-container'

interface NavItem {
  title: string
  href: string
  icon?: ReactNode
  children?: NavItem[]
}

const navItems: NavItem[] = [
  {
    title: "Dashboard",
    href: "/dashboard",
  },
  {
    title: "Analytics",
    href: "/dashboard/analytics",
  },
  {
    title: "Plugins",
    href: "/dashboard/plugins",
    children: [
      {
        title: "Classic View",
        href: "/dashboard/plugins",
      },
      {
        title: "Modern View",
        href: "/dashboard/plugins-new",
      },
    ],
  },
  {
    title: "Routes",
    href: "/dashboard/routes",
  },
  {
    title: "Configuration",
    href: "/dashboard/config",
  },
  {
    title: "System Metrics",
    href: "/dashboard/metrics",
  },
  {
    title: "AI Management",
    href: "/dashboard/ai",
    children: [
      {
        title: "Models",
        href: "/dashboard/ai/models",
      },
      {
        title: "Routing Rules",
        href: "/dashboard/ai/routing",
      },
      {
        title: "Prompt Templates",
        href: "/dashboard/ai-features/prompts",
      },
      {
        title: "Vector Databases",
        href: "/dashboard/ai-features/vector-db",
      },
    ],
  },
]

export function ResponsiveDashboardLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-40 border-b bg-background">
        <div className="container flex h-16 items-center justify-between py-4">
          <div className="flex items-center gap-4">
            <MobileNav items={navItems} />
            <Link href="/" className="flex items-center gap-2">
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
                        <NavigationMenuTrigger>{item.title}</NavigationMenuTrigger>
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
                                      {child.title}
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
                        href={item.href}
                        className={cn(
                          "block select-none space-y-1 rounded-md p-3 leading-none no-underline outline-none transition-colors hover:bg-accent hover:text-accent-foreground focus:bg-accent focus:text-accent-foreground"
                        )}
                      >
                        <div className="text-sm font-medium leading-none">
                          {item.title}
                        </div>
                      </Link>
                    )}
                  </NavigationMenuItem>
                ))}
              </NavigationMenuList>
            </NavigationMenu>
          </div>
          <div className="flex items-center gap-2">
            <ThemeToggle />
            <UserProfile />
          </div>
        </div>
      </header>
      <main className="flex-1">
        <ResponsiveContainer className="py-6">
          {children}
        </ResponsiveContainer>
      </main>
      <footer className="border-t py-6 md:py-0">
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
