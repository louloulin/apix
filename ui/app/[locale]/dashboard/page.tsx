"use client"

import { useTranslations } from 'next-intl'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { TrafficChart } from "@/components/dashboard/traffic-chart"
import { LlmUsageChart } from "@/components/dashboard/llm-usage-chart"
import {
  Activity,
  AlertTriangle,
  BarChart,
  Clock,
  Server,
  Zap
} from "lucide-react"

export default function LocalizedDashboardPage() {
  const t = useTranslations('dashboard')
  const common = useTranslations('common')

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-3xl font-bold">{t('title')}</h1>
      <p className="text-muted-foreground">
        {t('welcome')}
      </p>

      <Tabs defaultValue="overview" className="mt-6">
        <TabsList>
          <TabsTrigger value="overview">{t('overview')}</TabsTrigger>
          <TabsTrigger value="analytics">{t('analytics')}</TabsTrigger>
          <TabsTrigger value="llm-usage">{t('llmUsage')}</TabsTrigger>
        </TabsList>
        <TabsContent value="overview" className="space-y-4">
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  {t('totalRequests')}
                </CardTitle>
                <Activity className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">132,456</div>
                <p className="text-xs text-muted-foreground">
                  +12.5% {t('fromLastMonth')}
                </p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  {t('avgResponseTime')}
                </CardTitle>
                <Clock className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">45ms</div>
                <p className="text-xs text-muted-foreground">
                  -5ms {t('fromLastMonth')}
                </p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  {t('activePlugins')}
                </CardTitle>
                <Server className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">12</div>
                <p className="text-xs text-muted-foreground">
                  +2 {t('fromLastMonth')}
                </p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  {t('errorRate')}
                </CardTitle>
                <AlertTriangle className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">0.12%</div>
                <p className="text-xs text-muted-foreground">
                  -0.04% {t('fromLastWeek')}
                </p>
              </CardContent>
            </Card>
          </div>
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-7">
            <Card className="col-span-4">
              <CardHeader>
                <CardTitle>{t('requestTraffic')}</CardTitle>
                <CardDescription>
                  {t('requestVolume')}
                </CardDescription>
              </CardHeader>
              <CardContent className="h-[300px]">
                <TrafficChart />
              </CardContent>
            </Card>
            <Card className="col-span-3">
              <CardHeader>
                <CardTitle>{t('llmUsage')}</CardTitle>
                <CardDescription>
                  {t('distributionByProvider')}
                </CardDescription>
              </CardHeader>
              <CardContent className="h-[300px]">
                <LlmUsageChart />
              </CardContent>
            </Card>
          </div>
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle>{t('recentEvents')}</CardTitle>
                <CardDescription>
                  {t('lastEvents', { count: 5 })}
                </CardDescription>
              </CardHeader>
              <CardContent>
                <ul className="space-y-2">
                  <li className="flex items-center gap-2">
                    <span className="flex h-2 w-2 rounded-full bg-green-500"></span>
                    <span className="font-medium">{t('newRouteAdded')}</span>
                    <span className="text-sm text-muted-foreground ml-auto">5 {t('minutesAgo')}</span>
                  </li>
                  <li className="flex items-center gap-2">
                    <span className="flex h-2 w-2 rounded-full bg-blue-500"></span>
                    <span className="font-medium">{t('pluginEnabled')}</span>
                    <span className="text-sm text-muted-foreground ml-auto">15 {t('minutesAgo')}</span>
                  </li>
                  <li className="flex items-center gap-2">
                    <span className="flex h-2 w-2 rounded-full bg-yellow-500"></span>
                    <span className="font-medium">{t('highTrafficAlert')}</span>
                    <span className="text-sm text-muted-foreground ml-auto">30 {t('minutesAgo')}</span>
                  </li>
                  <li className="flex items-center gap-2">
                    <span className="flex h-2 w-2 rounded-full bg-red-500"></span>
                    <span className="font-medium">{t('errorRateIncreased')}</span>
                    <span className="text-sm text-muted-foreground ml-auto">1 {t('hourAgo')}</span>
                  </li>
                  <li className="flex items-center gap-2">
                    <span className="flex h-2 w-2 rounded-full bg-purple-500"></span>
                    <span className="font-medium">{t('configUpdated')}</span>
                    <span className="text-sm text-muted-foreground ml-auto">2 {t('hoursAgo')}</span>
                  </li>
                </ul>
              </CardContent>
            </Card>
            <Card>
              <CardHeader>
                <CardTitle>{t('activeRoutes')}</CardTitle>
                <CardDescription>
                  {t('topActiveRoutes', { count: 5 })}
                </CardDescription>
              </CardHeader>
              <CardContent>
                <ul className="space-y-2">
                  <li className="flex items-center justify-between">
                    <span className="font-medium">/api/v1/chat</span>
                    <span className="text-sm">45,231 {t('requests')}</span>
                  </li>
                  <li className="flex items-center justify-between">
                    <span className="font-medium">/api/v1/completions</span>
                    <span className="text-sm">32,145 {t('requests')}</span>
                  </li>
                  <li className="flex items-center justify-between">
                    <span className="font-medium">/api/v1/embeddings</span>
                    <span className="text-sm">21,654 {t('requests')}</span>
                  </li>
                  <li className="flex items-center justify-between">
                    <span className="font-medium">/api/v2/chat</span>
                    <span className="text-sm">18,432 {t('requests')}</span>
                  </li>
                  <li className="flex items-center justify-between">
                    <span className="font-medium">/api/v1/models</span>
                    <span className="text-sm">12,543 {t('requests')}</span>
                  </li>
                </ul>
              </CardContent>
            </Card>
          </div>
        </TabsContent>
        <TabsContent value="analytics" className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>{t('advancedAnalytics')}</CardTitle>
              <CardDescription>
                {t('detailedMetrics')}
              </CardDescription>
            </CardHeader>
            <CardContent className="h-[400px]">
              <div className="flex h-full items-center justify-center rounded-md border border-dashed">
                <div className="text-center">
                  <p className="text-muted-foreground">{t('analyticsComingSoon')}</p>
                </div>
              </div>
            </CardContent>
          </Card>
        </TabsContent>
        <TabsContent value="llm-usage" className="space-y-4">
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  {t('totalTokens')}
                </CardTitle>
                <BarChart className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">2.4M</div>
                <p className="text-xs text-muted-foreground">
                  +15.2% {t('fromLastMonth')}
                </p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  {t('avgTokensPerRequest')}
                </CardTitle>
                <Activity className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">1,250</div>
                <p className="text-xs text-muted-foreground">
                  +3.1% {t('fromLastMonth')}
                </p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  {t('mostUsedModel')}
                </CardTitle>
                <Zap className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">GPT-3.5</div>
                <p className="text-xs text-muted-foreground">
                  72% {t('ofRequests')}
                </p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  {t('estimatedCost')}
                </CardTitle>
                <AlertTriangle className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">$42.15</div>
                <p className="text-xs text-muted-foreground">
                  +8.3% {t('fromLastMonth')}
                </p>
              </CardContent>
            </Card>
          </div>

          <Card>
            <CardHeader>
              <CardTitle>{t('llmProviderUsage')}</CardTitle>
              <CardDescription>
                {t('requestDistribution')}
              </CardDescription>
            </CardHeader>
            <CardContent className="h-[360px]">
              <LlmUsageChart />
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  )
}
