"use client"

import { useState } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Button } from "@/components/ui/button"
import { useTranslations } from 'next-intl'
import {
  Activity,
  BarChart3,
  Clock,
  Download,
  RefreshCw,
  Server,
  Zap
} from "lucide-react"

export default function A11yDashboardPage() {
  const t = useTranslations()
  const [activeTab, setActiveTab] = useState("overview")

  return (
    <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold" id="page-title">{t('dashboard.title')}</h1>
            <p className="text-muted-foreground">
              {t('dashboard.welcome')}
            </p>
          </div>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="icon"
              aria-label={t('common.refresh')}
            >
              <RefreshCw className="h-4 w-4" />
            </Button>
            <Button>
              <Download className="mr-2 h-4 w-4" />
              {t('dashboard.exportReport')}
            </Button>
          </div>
        </div>

        <Tabs
          defaultValue="overview"
          value={activeTab}
          onValueChange={setActiveTab}
          aria-label={t('dashboard.sections')}
        >
          <TabsList className="grid w-full grid-cols-4">
            <TabsTrigger value="overview" className="flex items-center gap-2">
              <BarChart3 className="h-4 w-4" />
              <span>{t('dashboard.overview')}</span>
            </TabsTrigger>
            <TabsTrigger value="performance" className="flex items-center gap-2">
              <Activity className="h-4 w-4" />
              <span>{t('dashboard.performance')}</span>
            </TabsTrigger>
            <TabsTrigger value="ai" className="flex items-center gap-2">
              <Zap className="h-4 w-4" />
              <span>{t('dashboard.aiUsage')}</span>
            </TabsTrigger>
            <TabsTrigger value="system" className="flex items-center gap-2">
              <Server className="h-4 w-4" />
              <span>{t('dashboard.system')}</span>
            </TabsTrigger>
          </TabsList>

          <TabsContent value="overview" className="space-y-4">
            <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
              <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    {t('dashboard.totalRequests')}
                  </CardTitle>
                  <Activity className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">1,234,567</div>
                  <p className="text-xs text-muted-foreground">
                    +12.5% from last month
                  </p>
                </CardContent>
              </Card>
              <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    {t('dashboard.successRate')}
                  </CardTitle>
                  <Zap className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">99.8%</div>
                  <p className="text-xs text-muted-foreground">
                    +0.2% from last month
                  </p>
                </CardContent>
              </Card>
              <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    {t('dashboard.avgResponseTime')}
                  </CardTitle>
                  <Clock className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">125ms</div>
                  <p className="text-xs text-muted-foreground">
                    -15ms from last month
                  </p>
                </CardContent>
              </Card>
              <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    {t('dashboard.activeRoutes')}
                  </CardTitle>
                  <Server className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">42</div>
                  <p className="text-xs text-muted-foreground">
                    +5 from last month
                  </p>
                </CardContent>
              </Card>
            </div>

            <Card>
              <CardHeader>
                <CardTitle>{t('dashboard.recentActivity')}</CardTitle>
                <CardDescription>
                  {t('dashboard.latestEvents')}
                </CardDescription>
              </CardHeader>
              <CardContent>
                <ul className="space-y-4" aria-label="Recent activities">
                  <li className="flex items-center gap-4">
                    <div className="h-2 w-2 rounded-full bg-green-500" aria-hidden="true"></div>
                    <div className="flex-1">
                      <p className="text-sm font-medium">Route added: /api/v2/users</p>
                      <p className="text-xs text-muted-foreground">Today, 10:30 AM</p>
                    </div>
                  </li>
                  <li className="flex items-center gap-4">
                    <div className="h-2 w-2 rounded-full bg-yellow-500" aria-hidden="true"></div>
                    <div className="flex-1">
                      <p className="text-sm font-medium">High memory usage detected (75%)</p>
                      <p className="text-xs text-muted-foreground">Today, 9:15 AM</p>
                    </div>
                  </li>
                  <li className="flex items-center gap-4">
                    <div className="h-2 w-2 rounded-full bg-blue-500" aria-hidden="true"></div>
                    <div className="flex-1">
                      <p className="text-sm font-medium">Plugin updated: rate-limiter v2.1.0</p>
                      <p className="text-xs text-muted-foreground">Yesterday, 4:45 PM</p>
                    </div>
                  </li>
                  <li className="flex items-center gap-4">
                    <div className="h-2 w-2 rounded-full bg-red-500" aria-hidden="true"></div>
                    <div className="flex-1">
                      <p className="text-sm font-medium">Error spike detected (5xx responses)</p>
                      <p className="text-xs text-muted-foreground">Yesterday, 2:30 PM</p>
                    </div>
                  </li>
                  <li className="flex items-center gap-4">
                    <div className="h-2 w-2 rounded-full bg-green-500" aria-hidden="true"></div>
                    <div className="flex-1">
                      <p className="text-sm font-medium">System update completed</p>
                      <p className="text-xs text-muted-foreground">Yesterday, 1:00 PM</p>
                    </div>
                  </li>
                </ul>
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="performance" className="space-y-4">
            <Card>
              <CardHeader>
                <CardTitle>{t('dashboard.performance')}</CardTitle>
                <CardDescription>
                  Performance metrics for your gateway
                </CardDescription>
              </CardHeader>
              <CardContent>
                <div className="text-center py-8">
                  <p>{t('common.noData')}</p>
                </div>
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="ai" className="space-y-4">
            <Card>
              <CardHeader>
                <CardTitle>{t('dashboard.aiUsage')}</CardTitle>
                <CardDescription>
                  AI usage metrics for your gateway
                </CardDescription>
              </CardHeader>
              <CardContent>
                <div className="text-center py-8">
                  <p>{t('common.noData')}</p>
                </div>
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="system" className="space-y-4">
            <Card>
              <CardHeader>
                <CardTitle>{t('dashboard.system')}</CardTitle>
                <CardDescription>
                  System metrics for your gateway
                </CardDescription>
              </CardHeader>
              <CardContent>
                <div className="text-center py-8">
                  <p>{t('common.noData')}</p>
                </div>
              </CardContent>
            </Card>
          </TabsContent>
        </Tabs>
      </div>
  )
}
