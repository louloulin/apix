"use client"

import { useState } from "react"
import Link from "next/link"
import { usePathname } from "next/navigation"
import { Button } from "@/components/ui/button"
import { Sheet, SheetContent, SheetTrigger } from "@/components/ui/sheet"
import { cn } from "@/lib/utils"
import { Menu, X } from "lucide-react"

interface NavItem {
  title: string
  href: string
  icon?: React.ReactNode
  children?: NavItem[]
}

interface MobileNavProps {
  items: NavItem[]
}

export function MobileNav({ items }: MobileNavProps) {
  const [open, setOpen] = useState(false)
  const pathname = usePathname()
  
  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger asChild>
        <Button variant="ghost" size="icon" className="md:hidden">
          <Menu className="h-5 w-5" />
          <span className="sr-only">Toggle menu</span>
        </Button>
      </SheetTrigger>
      <SheetContent side="left" className="w-[240px] sm:w-[300px] pr-0">
        <div className="flex items-center justify-between pr-4">
          <Link href="/" className="flex items-center" onClick={() => setOpen(false)}>
            <span className="font-bold">APIX AI Gateway</span>
          </Link>
          <Button variant="ghost" size="icon" onClick={() => setOpen(false)}>
            <X className="h-5 w-5" />
            <span className="sr-only">Close menu</span>
          </Button>
        </div>
        <nav className="mt-6 flex flex-col gap-4 pr-4">
          {items.map((item, index) => (
            <div key={index}>
              {item.children ? (
                <div className="flex flex-col gap-2">
                  <div className="font-medium">{item.title}</div>
                  <div className="flex flex-col gap-1 pl-4">
                    {item.children.map((child, childIndex) => (
                      <Link
                        key={childIndex}
                        href={child.href}
                        className={cn(
                          "text-muted-foreground hover:text-foreground",
                          pathname === child.href && "text-foreground font-medium"
                        )}
                        onClick={() => setOpen(false)}
                      >
                        {child.title}
                      </Link>
                    ))}
                  </div>
                </div>
              ) : (
                <Link
                  href={item.href}
                  className={cn(
                    "font-medium hover:text-foreground",
                    pathname === item.href && "text-foreground font-medium"
                  )}
                  onClick={() => setOpen(false)}
                >
                  {item.title}
                </Link>
              )}
            </div>
          ))}
        </nav>
      </SheetContent>
    </Sheet>
  )
}
