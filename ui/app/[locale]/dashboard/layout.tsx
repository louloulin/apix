"use client"

import { I18nDashboardLayout } from "@/components/layout/i18n-dashboard-layout"

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <I18nDashboardLayout>
      {children}
    </I18nDashboardLayout>
  )
}
