"use client"

import { useState } from "react"
import { useTranslations } from 'next-intl'
import { I18nDashboardLayout } from "@/components/layout/i18n-dashboard-layout"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Button } from "@/components/ui/button"
import { EnhancedAIUsageChart } from "@/components/dashboard/enhanced-ai-usage-chart"
import { SystemHealthStatus } from "@/components/dashboard/system-health-status"
import { 
  Activity, 
  BarChart3, 
  Clock, 
  Download, 
  RefreshCw, 
  Server, 
  Zap,
  Cpu,
  Memory,
  HardDrive,
  Network,
  AlertTriangle
} from "lucide-react"

// Mock data for AI usage
const mockAIUsageData = [
  {
    date: "2023-06-01",
    requests: 1200,
    tokens: 240000,
    cost: 4.8,
    models: {
      "gpt-4": { requests: 300, tokens: 90000, cost: 2.7 },
      "gpt-3.5-turbo": { requests: 900, tokens: 150000, cost: 2.1 }
    }
  },
  {
    date: "2023-06-02",
    requests: 1350,
    tokens: 270000,
    cost: 5.4,
    models: {
      "gpt-4": { requests: 350, tokens: 105000, cost: 3.15 },
      "gpt-3.5-turbo": { requests: 1000, tokens: 165000, cost: 2.25 }
    }
  },
  {
    date: "2023-06-03",
    requests: 1500,
    tokens: 300000,
    cost: 6.0,
    models: {
      "gpt-4": { requests: 400, tokens: 120000, cost: 3.6 },
      "gpt-3.5-turbo": { requests: 1100, tokens: 180000, cost: 2.4 }
    }
  },
  {
    date: "2023-06-04",
    requests: 1650,
    tokens: 330000,
    cost: 6.6,
    models: {
      "gpt-4": { requests: 450, tokens: 135000, cost: 4.05 },
      "gpt-3.5-turbo": { requests: 1200, tokens: 195000, cost: 2.55 }
    }
  },
  {
    date: "2023-06-05",
    requests: 1800,
    tokens: 360000,
    cost: 7.2,
    models: {
      "gpt-4": { requests: 500, tokens: 150000, cost: 4.5 },
      "gpt-3.5-turbo": { requests: 1300, tokens: 210000, cost: 2.7 }
    }
  },
  {
    date: "2023-06-06",
    requests: 1950,
    tokens: 390000,
    cost: 7.8,
    models: {
      "gpt-4": { requests: 550, tokens: 165000, cost: 4.95 },
      "gpt-3.5-turbo": { requests: 1400, tokens: 225000, cost: 2.85 }
    }
  },
  {
    date: "2023-06-07",
    requests: 2100,
    tokens: 420000,
    cost: 8.4,
    models: {
      "gpt-4": { requests: 600, tokens: 180000, cost: 5.4 },
      "gpt-3.5-turbo": { requests: 1500, tokens: 240000, cost: 3.0 }
    }
  }
];

export default function EnhancedDashboardPage() {
  const t = useTranslations('dashboard')
  const common = useTranslations('common')
  const [activeTab, setActiveTab] = useState("overview")
  
  return (
    <I18nDashboardLayout>
      <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold">{t('title')}</h1>
            <p className="text-muted-foreground">
              {t('welcome')}
            </p>
          </div>
          <div className="flex gap-2">
            <Button variant="outline" size="icon">
              <RefreshCw className="h-4 w-4" />
            </Button>
            <Button>
              <Download className="mr-2 h-4 w-4" />
              {t('exportReport')}
            </Button>
          </div>
        </div>
        
        <Tabs defaultValue={activeTab} value={activeTab} onValueChange={setActiveTab}>
          <TabsList>
            <TabsTrigger value="overview" className="flex items-center gap-2">
              <Activity className="h-4 w-4" />
              {t('overview')}
            </TabsTrigger>
            <TabsTrigger value="ai" className="flex items-center gap-2">
              <Zap className="h-4 w-4" />
              {t('aiUsage')}
            </TabsTrigger>
            <TabsTrigger value="system" className="flex items-center gap-2">
              <Server className="h-4 w-4" />
              {t('system')}
            </TabsTrigger>
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
                  <div className="text-2xl font-bold">1,234,567</div>
                  <p className="text-xs text-muted-foreground">
                    +12.5% from last month
                  </p>
                </CardContent>
              </Card>
              <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    {t('successRate')}
                  </CardTitle>
                  <Zap className="h-4 w-4 text-muted-foreground" />
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
                    {t('avgResponseTime')}
                  </CardTitle>
                  <Clock className="h-4 w-4 text-muted-foreground" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">45ms</div>
                  <p className="text-xs text-muted-foreground">
                    -5ms from last month
                  </p>
                </CardContent>
              </Card>
              <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    {t('activeRoutes')}
                  </CardTitle>
                  <BarChart3 className="h-4 w-4 text-muted-foreground" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">24</div>
                  <p className="text-xs text-muted-foreground">
                    +2 from last month
                  </p>
                </CardContent>
              </Card>
            </div>
            
            <div className="grid gap-4 md:grid-cols-2">
              <EnhancedAIUsageChart data={mockAIUsageData} />
              <SystemHealthStatus />
            </div>
            
            <Card>
              <CardHeader>
                <CardTitle>{t('recentActivity')}</CardTitle>
                <CardDescription>
                  {t('latestEvents')}
                </CardDescription>
              </CardHeader>
              <CardContent>
                <div className="space-y-4">
                  <div className="flex items-center gap-4">
                    <div className="rounded-full p-2 bg-green-100 dark:bg-green-900">
                      <Zap className="h-4 w-4 text-green-600 dark:text-green-400" />
                    </div>
                    <div className="flex-1">
                      <p className="font-medium">New route added</p>
                      <p className="text-sm text-muted-foreground">
                        Route "/api/v2/chat" was added
                      </p>
                    </div>
                    <div className="text-sm text-muted-foreground">
                      5 minutes ago
                    </div>
                  </div>
                  <div className="flex items-center gap-4">
                    <div className="rounded-full p-2 bg-blue-100 dark:bg-blue-900">
                      <Server className="h-4 w-4 text-blue-600 dark:text-blue-400" />
                    </div>
                    <div className="flex-1">
                      <p className="font-medium">Plugin enabled</p>
                      <p className="text-sm text-muted-foreground">
                        Rate limiter plugin was enabled
                      </p>
                    </div>
                    <div className="text-sm text-muted-foreground">
                      15 minutes ago
                    </div>
                  </div>
                  <div className="flex items-center gap-4">
                    <div className="rounded-full p-2 bg-yellow-100 dark:bg-yellow-900">
                      <AlertTriangle className="h-4 w-4 text-yellow-600 dark:text-yellow-400" />
                    </div>
                    <div className="flex-1">
                      <p className="font-medium">High traffic alert</p>
                      <p className="text-sm text-muted-foreground">
                        Traffic spike detected on "/api/v1/chat"
                      </p>
                    </div>
                    <div className="text-sm text-muted-foreground">
                      30 minutes ago
                    </div>
                  </div>
                </div>
              </CardContent>
            </Card>
          </TabsContent>
          
          <TabsContent value="ai" className="space-y-4">
            <EnhancedAIUsageChart data={mockAIUsageData} className="h-[500px]" />
            
            <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
              <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    Most Used Model
                  </CardTitle>
                  <Zap className="h-4 w-4 text-muted-foreground" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">GPT-3.5 Turbo</div>
                  <p className="text-xs text-muted-foreground">
                    71.4% of all requests
                  </p>
                </CardContent>
              </Card>
              <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    Total Tokens
                  </CardTitle>
                  <BarChart3 className="h-4 w-4 text-muted-foreground" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">2.31M</div>
                  <p className="text-xs text-muted-foreground">
                    +15.2% from last month
                  </p>
                </CardContent>
              </Card>
              <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    Total Cost
                  </CardTitle>
                  <Activity className="h-4 w-4 text-muted-foreground" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">$46.20</div>
                  <p className="text-xs text-muted-foreground">
                    +8.5% from last month
                  </p>
                </CardContent>
              </Card>
            </div>
          </TabsContent>
          
          <TabsContent value="system" className="space-y-4">
            <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
              <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    CPU Usage
                  </CardTitle>
                  <Cpu className="h-4 w-4 text-muted-foreground" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">24.5%</div>
                  <p className="text-xs text-muted-foreground">
                    8 cores @ 3.5 GHz
                  </p>
                </CardContent>
              </Card>
              <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    Memory Usage
                  </CardTitle>
                  <Memory className="h-4 w-4 text-muted-foreground" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">4.2 GB</div>
                  <p className="text-xs text-muted-foreground">
                    of 16 GB (26.3%)
                  </p>
                </CardContent>
              </Card>
              <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    Disk Usage
                  </CardTitle>
                  <HardDrive className="h-4 w-4 text-muted-foreground" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">128 GB</div>
                  <p className="text-xs text-muted-foreground">
                    of 512 GB (25%)
                  </p>
                </CardContent>
              </Card>
              <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    Network
                  </CardTitle>
                  <Network className="h-4 w-4 text-muted-foreground" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">45 Mbps</div>
                  <p className="text-xs text-muted-foreground">
                    1.2 TB transferred today
                  </p>
                </CardContent>
              </Card>
            </div>
            
            <SystemHealthStatus className="h-[400px]" />
          </TabsContent>
        </Tabs>
      </div>
    </I18nDashboardLayout>
  )
}
