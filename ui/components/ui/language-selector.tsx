"use client"

import { useTransition } from "react"
import { useRouter, usePathname } from "next/navigation"
import { useLocale } from "next-intl"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { locales } from "@/i18n"

interface LanguageSelectorProps {
  className?: string
}

export function LanguageSelector({ className }: LanguageSelectorProps) {
  const [isPending, startTransition] = useTransition()
  const locale = useLocale()
  const router = useRouter()
  const pathname = usePathname()
  
  // Get the path without the locale prefix
  const pathnameWithoutLocale = pathname.replace(`/${locale}`, '')
  
  // Handle locale change
  const handleLocaleChange = (newLocale: string) => {
    startTransition(() => {
      router.replace(`/${newLocale}${pathnameWithoutLocale}`)
    })
  }
  
  // Map of locale to display name
  const localeNames: Record<string, string> = {
    en: "English",
    zh: "中文"
  }
  
  return (
    <Select
      value={locale}
      onValueChange={handleLocaleChange}
      disabled={isPending}
    >
      <SelectTrigger className={className}>
        <SelectValue placeholder="Select language" />
      </SelectTrigger>
      <SelectContent>
        {locales.map((l) => (
          <SelectItem key={l} value={l}>
            {localeNames[l]}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
