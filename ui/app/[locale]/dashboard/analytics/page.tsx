"use client"

import { useTranslations } from 'next-intl'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Button } from "@/components/ui/button"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import {
  Activity,
  BarChart3,
  Clock,
  Download,
  MessagesSquare as MessagesSquareIcon,
  ClockIcon,
  BarChart,
  Zap,
  Server
} from "lucide-react"

export default function LocalizedAnalyticsPage() {
  const t = useTranslations('dashboard')
  const common = useTranslations('common')

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">{common('analytics')}</h1>
          <p className="text-muted-foreground">
            Monitor your AI Gateway performance and usage metrics
          </p>
        </div>
        <div className="flex gap-2">
          <Select defaultValue="30d">
            <SelectTrigger className="w-[180px]">
              <SelectValue placeholder="Time Range" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="24h">Last 24 hours</SelectItem>
              <SelectItem value="7d">Last 7 days</SelectItem>
              <SelectItem value="30d">Last 30 days</SelectItem>
              <SelectItem value="90d">Last 90 days</SelectItem>
            </SelectContent>
          </Select>
          <Button>Export Data</Button>
        </div>
      </div>

      <Tabs defaultValue="overview" className="mt-6">
        <TabsList>
          <TabsTrigger value="overview">{t('overview')}</TabsTrigger>
          <TabsTrigger value="traffic">{t('requestTraffic')}</TabsTrigger>
          <TabsTrigger value="ai-usage">{t('aiUsage')}</TabsTrigger>
        </TabsList>

        <TabsContent value="overview" className="space-y-4">
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  Total AI Requests
                </CardTitle>
                <MessagesSquareIcon className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">87,392</div>
                <p className="text-xs text-muted-foreground">
                  +15.3% from previous period
                </p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  Avg. Response Time
                </CardTitle>
                <Clock className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">267ms</div>
                <p className="text-xs text-muted-foreground">
                  -12.5% from previous period
                </p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  Success Rate
                </CardTitle>
                <Activity className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">99.7%</div>
                <p className="text-xs text-muted-foreground">
                  +0.2% from previous period
                </p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">
                  Total Tokens
                </CardTitle>
                <BarChart className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">3.2M</div>
                <p className="text-xs text-muted-foreground">
                  +8.1% from previous period
                </p>
              </CardContent>
            </Card>
          </div>

          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-7">
            <Card className="col-span-4">
              <CardHeader>
                <CardTitle>Request Volume</CardTitle>
                <CardDescription>
                  Total requests over time
                </CardDescription>
              </CardHeader>
              <CardContent className="h-[300px]">
                <div className="h-full w-full flex items-center justify-center border border-dashed rounded-md">
                  <p className="text-muted-foreground">Chart visualization will be implemented here</p>
                </div>
              </CardContent>
            </Card>
            <Card className="col-span-3">
              <CardHeader>
                <CardTitle>Response Time Distribution</CardTitle>
                <CardDescription>
                  Response time percentiles
                </CardDescription>
              </CardHeader>
              <CardContent className="h-[300px]">
                <div className="h-full w-full flex items-center justify-center border border-dashed rounded-md">
                  <p className="text-muted-foreground">Chart visualization will be implemented here</p>
                </div>
              </CardContent>
            </Card>
          </div>
        </TabsContent>

        <TabsContent value="traffic" className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>Traffic Analysis</CardTitle>
              <CardDescription>
                Detailed traffic patterns and request distribution
              </CardDescription>
            </CardHeader>
            <CardContent className="h-[400px]">
              <div className="h-full w-full flex items-center justify-center border border-dashed rounded-md">
                <p className="text-muted-foreground">Traffic analysis visualization will be implemented here</p>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="ai-usage" className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>AI Model Usage</CardTitle>
              <CardDescription>
                Usage distribution across AI models
              </CardDescription>
            </CardHeader>
            <CardContent className="h-[400px]">
              <div className="h-full w-full flex items-center justify-center border border-dashed rounded-md">
                <p className="text-muted-foreground">AI usage visualization will be implemented here</p>
              </div>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  )
}
