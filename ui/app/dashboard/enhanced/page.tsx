"use client"

import { useState } from "react"
import { DashboardLayout } from "@/components/layout/dashboard-layout"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Button } from "@/components/ui/button"
import { EnhancedTrafficChart } from "@/components/dashboard/enhanced-traffic-chart"
import { EnhancedLLMUsageChart } from "@/components/dashboard/enhanced-llm-usage-chart"
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
import { useApiData } from "@/lib/hooks/use-api-data"
import { metricsApi } from "@/lib/api-client"

export default function EnhancedDashboardPage() {
  const [activeTab, setActiveTab] = useState("overview")
  
  // Fetch metrics data
  const { 
    data: metricsData, 
    isLoading, 
    error, 
    refetch,
    isRefetching
  } = useApiData(
    () => metricsApi.getMetrics(),
    {
      onError: (err) => {
        console.error("Error fetching metrics:", err)
      }
    }
  )
  
  // Format bytes to human-readable format
  const formatBytes = (bytes: number, decimals = 2) => {
    if (bytes === 0) return '0 Bytes'
    
    const k = 1024
    const dm = decimals < 0 ? 0 : decimals
    const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB']
    
    const i = Math.floor(Math.log(bytes) / Math.log(k))
    
    return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i]
  }
  
  // Format milliseconds to human-readable format
  const formatTime = (ms: number) => {
    const seconds = Math.floor(ms / 1000)
    const minutes = Math.floor(seconds / 60)
    const hours = Math.floor(minutes / 60)
    const days = Math.floor(hours / 24)
    
    if (days > 0) {
      return `${days}d ${hours % 24}h ${minutes % 60}m`
    } else if (hours > 0) {
      return `${hours}h ${minutes % 60}m ${seconds % 60}s`
    } else if (minutes > 0) {
      return `${minutes}m ${seconds % 60}s`
    } else {
      return `${seconds}s`
    }
  }
  
  return (
    <DashboardLayout>
      <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold">Dashboard</h1>
            <p className="text-muted-foreground">
              Welcome to the APIX AI Gateway dashboard
            </p>
          </div>
          <div className="flex gap-2">
            <Button 
              variant="outline" 
              size="icon" 
              onClick={() => refetch()}
              disabled={isRefetching}
            >
              <RefreshCw className={`h-4 w-4 ${isRefetching ? 'animate-spin' : ''}`} />
            </Button>
            <Button>
              <Download className="mr-2 h-4 w-4" />
              Export Report
            </Button>
          </div>
        </div>

        <Tabs defaultValue="overview" value={activeTab} onValueChange={setActiveTab}>
          <TabsList className="grid w-full grid-cols-4">
            <TabsTrigger value="overview" className="flex items-center gap-2">
              <BarChart3 className="h-4 w-4" />
              Overview
            </TabsTrigger>
            <TabsTrigger value="performance" className="flex items-center gap-2">
              <Activity className="h-4 w-4" />
              Performance
            </TabsTrigger>
            <TabsTrigger value="ai" className="flex items-center gap-2">
              <Zap className="h-4 w-4" />
              AI Usage
            </TabsTrigger>
            <TabsTrigger value="system" className="flex items-center gap-2">
              <Server className="h-4 w-4" />
              System
            </TabsTrigger>
          </TabsList>
          
          <TabsContent value="overview" className="space-y-4">
            <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
              <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    Total Requests
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
                    Success Rate
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
                    Avg. Response Time
                  </CardTitle>
                  <Clock className="h-4 w-4 text-muted-foreground" />
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
                    Active Routes
                  </CardTitle>
                  <Network className="h-4 w-4 text-muted-foreground" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">42</div>
                  <p className="text-xs text-muted-foreground">
                    +5 from last month
                  </p>
                </CardContent>
              </Card>
            </div>
            
            <div className="grid gap-4 md:grid-cols-2">
              <EnhancedTrafficChart />
              <EnhancedLLMUsageChart />
            </div>
            
            <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
              <SystemHealthStatus />
              
              <Card className="lg:col-span-2">
                <CardHeader>
                  <CardTitle>Recent Activity</CardTitle>
                  <CardDescription>
                    Latest events and alerts from your gateway
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  <div className="space-y-4">
                    <div className="flex items-center gap-4">
                      <div className="h-2 w-2 rounded-full bg-green-500"></div>
                      <div className="flex-1">
                        <p className="text-sm font-medium">Route added: /api/v2/users</p>
                        <p className="text-xs text-muted-foreground">Today, 10:30 AM</p>
                      </div>
                    </div>
                    <div className="flex items-center gap-4">
                      <div className="h-2 w-2 rounded-full bg-yellow-500"></div>
                      <div className="flex-1">
                        <p className="text-sm font-medium">High memory usage detected (75%)</p>
                        <p className="text-xs text-muted-foreground">Today, 9:15 AM</p>
                      </div>
                    </div>
                    <div className="flex items-center gap-4">
                      <div className="h-2 w-2 rounded-full bg-blue-500"></div>
                      <div className="flex-1">
                        <p className="text-sm font-medium">Plugin updated: rate-limiter v2.1.0</p>
                        <p className="text-xs text-muted-foreground">Yesterday, 4:45 PM</p>
                      </div>
                    </div>
                    <div className="flex items-center gap-4">
                      <div className="h-2 w-2 rounded-full bg-red-500"></div>
                      <div className="flex-1">
                        <p className="text-sm font-medium">Error spike detected (5xx responses)</p>
                        <p className="text-xs text-muted-foreground">Yesterday, 2:30 PM</p>
                      </div>
                    </div>
                    <div className="flex items-center gap-4">
                      <div className="h-2 w-2 rounded-full bg-green-500"></div>
                      <div className="flex-1">
                        <p className="text-sm font-medium">System update completed</p>
                        <p className="text-xs text-muted-foreground">Yesterday, 1:00 PM</p>
                      </div>
                    </div>
                  </div>
                </CardContent>
              </Card>
            </div>
          </TabsContent>
          
          <TabsContent value="performance" className="space-y-4">
            <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
              <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    Requests/Second
                  </CardTitle>
                  <Activity className="h-4 w-4 text-muted-foreground" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">1,250</div>
                  <p className="text-xs text-muted-foreground">
                    Peak: 2,500 req/s
                  </p>
                </CardContent>
              </Card>
              <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    P95 Latency
                  </CardTitle>
                  <Clock className="h-4 w-4 text-muted-foreground" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">185ms</div>
                  <p className="text-xs text-muted-foreground">
                    P99: 250ms
                  </p>
                </CardContent>
              </Card>
              <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    Error Rate
                  </CardTitle>
                  <AlertTriangle className="h-4 w-4 text-muted-foreground" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">0.2%</div>
                  <p className="text-xs text-muted-foreground">
                    -0.1% from last week
                  </p>
                </CardContent>
              </Card>
              <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    Concurrent Connections
                  </CardTitle>
                  <Network className="h-4 w-4 text-muted-foreground" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">5,280</div>
                  <p className="text-xs text-muted-foreground">
                    Peak: 12,500
                  </p>
                </CardContent>
              </Card>
            </div>
            
            <EnhancedTrafficChart 
              title="Performance Metrics" 
              description="Request volume, errors, and latency over time"
            />
            
            <div className="grid gap-4 md:grid-cols-2">
              <Card>
                <CardHeader>
                  <CardTitle>Top Routes by Traffic</CardTitle>
                  <CardDescription>
                    Routes with the highest request volume
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  <div className="space-y-4">
                    {[
                      { path: "/api/v1/users", requests: 450000, latency: 95 },
                      { path: "/api/v1/products", requests: 320000, latency: 120 },
                      { path: "/api/v1/auth/login", requests: 280000, latency: 150 },
                      { path: "/api/v1/orders", requests: 210000, latency: 180 },
                      { path: "/api/v1/search", requests: 180000, latency: 200 }
                    ].map((route, index) => (
                      <div key={index} className="flex items-center justify-between">
                        <div>
                          <p className="text-sm font-medium">{route.path}</p>
                          <p className="text-xs text-muted-foreground">{route.requests.toLocaleString()} requests</p>
                        </div>
                        <div className="text-sm">{route.latency}ms</div>
                      </div>
                    ))}
                  </div>
                </CardContent>
              </Card>
              
              <Card>
                <CardHeader>
                  <CardTitle>Error Distribution</CardTitle>
                  <CardDescription>
                    Breakdown of error types
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  <div className="space-y-4">
                    {[
                      { code: "5xx", count: 1250, percent: 45 },
                      { code: "4xx", count: 950, percent: 35 },
                      { code: "Timeouts", count: 350, percent: 12 },
                      { code: "Connection", count: 150, percent: 5 },
                      { code: "Other", count: 80, percent: 3 }
                    ].map((error, index) => (
                      <div key={index} className="space-y-1">
                        <div className="flex items-center justify-between">
                          <p className="text-sm font-medium">{error.code} Errors</p>
                          <p className="text-sm">{error.count.toLocaleString()} ({error.percent}%)</p>
                        </div>
                        <div className="h-2 w-full rounded-full bg-secondary">
                          <div 
                            className="h-full rounded-full bg-primary" 
                            style={{ width: `${error.percent}%` }}
                          ></div>
                        </div>
                      </div>
                    ))}
                  </div>
                </CardContent>
              </Card>
            </div>
          </TabsContent>
          
          <TabsContent value="ai" className="space-y-4">
            <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
              <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    Total AI Requests
                  </CardTitle>
                  <Zap className="h-4 w-4 text-muted-foreground" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">850,420</div>
                  <p className="text-xs text-muted-foreground">
                    +24.8% from last month
                  </p>
                </CardContent>
              </Card>
              <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    Tokens Processed
                  </CardTitle>
                  <Activity className="h-4 w-4 text-muted-foreground" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">125.8M</div>
                  <p className="text-xs text-muted-foreground">
                    +18.3% from last month
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
                  <div className="text-2xl font-bold">1.85s</div>
                  <p className="text-xs text-muted-foreground">
                    -0.3s from last month
                  </p>
                </CardContent>
              </Card>
              <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    Cost
                  </CardTitle>
                  <AlertTriangle className="h-4 w-4 text-muted-foreground" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">$1,250.42</div>
                  <p className="text-xs text-muted-foreground">
                    +$215.80 from last month
                  </p>
                </CardContent>
              </Card>
            </div>
            
            <EnhancedLLMUsageChart 
              title="AI Model Usage" 
              description="Distribution of requests, tokens, and cost by model"
            />
            
            <div className="grid gap-4 md:grid-cols-2">
              <Card>
                <CardHeader>
                  <CardTitle>Top AI Routes</CardTitle>
                  <CardDescription>
                    Routes with the highest AI usage
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  <div className="space-y-4">
                    {[
                      { path: "/api/v1/ai/chat", requests: 320000, tokens: 64000000 },
                      { path: "/api/v1/ai/completion", requests: 250000, tokens: 37500000 },
                      { path: "/api/v1/ai/embedding", requests: 180000, tokens: 18000000 },
                      { path: "/api/v1/ai/summarize", requests: 65000, tokens: 9750000 },
                      { path: "/api/v1/ai/translate", requests: 35000, tokens: 5250000 }
                    ].map((route, index) => (
                      <div key={index} className="flex items-center justify-between">
                        <div>
                          <p className="text-sm font-medium">{route.path}</p>
                          <p className="text-xs text-muted-foreground">{route.requests.toLocaleString()} requests</p>
                        </div>
                        <div className="text-sm">{(route.tokens / 1000000).toFixed(1)}M tokens</div>
                      </div>
                    ))}
                  </div>
                </CardContent>
              </Card>
              
              <Card>
                <CardHeader>
                  <CardTitle>Cost Breakdown</CardTitle>
                  <CardDescription>
                    AI cost by model
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  <div className="space-y-4">
                    {[
                      { model: "GPT-4", cost: 625.50, percent: 50 },
                      { model: "GPT-3.5 Turbo", cost: 187.65, percent: 15 },
                      { model: "Claude 3 Opus", cost: 250.20, percent: 20 },
                      { model: "Claude 3 Sonnet", cost: 125.10, percent: 10 },
                      { model: "Llama 3", cost: 62.55, percent: 5 }
                    ].map((model, index) => (
                      <div key={index} className="space-y-1">
                        <div className="flex items-center justify-between">
                          <p className="text-sm font-medium">{model.model}</p>
                          <p className="text-sm">${model.cost.toFixed(2)} ({model.percent}%)</p>
                        </div>
                        <div className="h-2 w-full rounded-full bg-secondary">
                          <div 
                            className="h-full rounded-full bg-primary" 
                            style={{ width: `${model.percent}%` }}
                          ></div>
                        </div>
                      </div>
                    ))}
                  </div>
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
                  <div className="text-2xl font-bold">
                    {isLoading ? "Loading..." : `${(metricsData?.cpu?.processCpuLoad * 100 || 0).toFixed(1)}%`}
                  </div>
                  <p className="text-xs text-muted-foreground">
                    {isLoading ? "" : `${metricsData?.cpu?.cores || 0} cores available`}
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
                  <div className="text-2xl font-bold">
                    {isLoading ? "Loading..." : formatBytes(metricsData?.memory?.heap?.used || 0)}
                  </div>
                  <p className="text-xs text-muted-foreground">
                    {isLoading ? "" : `${((metricsData?.memory?.heap?.used || 0) / (metricsData?.memory?.heap?.max || 1) * 100).toFixed(1)}% of ${formatBytes(metricsData?.memory?.heap?.max || 0)}`}
                  </p>
                </CardContent>
              </Card>
              <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    Thread Count
                  </CardTitle>
                  <HardDrive className="h-4 w-4 text-muted-foreground" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">
                    {isLoading ? "Loading..." : metricsData?.threads?.count || 0}
                  </div>
                  <p className="text-xs text-muted-foreground">
                    {isLoading ? "" : `Peak: ${metricsData?.threads?.peakCount || 0}`}
                  </p>
                </CardContent>
              </Card>
              <Card>
                <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                  <CardTitle className="text-sm font-medium">
                    Uptime
                  </CardTitle>
                  <Server className="h-4 w-4 text-muted-foreground" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">
                    {isLoading ? "Loading..." : formatTime(metricsData?.jvm?.uptime || 0)}
                  </div>
                  <p className="text-xs text-muted-foreground">
                    {isLoading ? "" : `Started: ${new Date(metricsData?.jvm?.startTime || 0).toLocaleString()}`}
                  </p>
                </CardContent>
              </Card>
            </div>
            
            <div className="grid gap-4 md:grid-cols-2">
              <SystemHealthStatus />
              
              <Card>
                <CardHeader>
                  <CardTitle>JVM Information</CardTitle>
                  <CardDescription>
                    Java Virtual Machine details
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  {isLoading ? (
                    <div className="flex h-[200px] items-center justify-center">
                      <div className="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent"></div>
                    </div>
                  ) : metricsData?.jvm ? (
                    <div className="space-y-4">
                      <div className="grid grid-cols-2 gap-4">
                        <div>
                          <h4 className="text-sm font-medium">JVM Name</h4>
                          <p className="text-lg font-medium">{metricsData.jvm.name}</p>
                        </div>
                        
                        <div>
                          <h4 className="text-sm font-medium">JVM Version</h4>
                          <p className="text-lg font-medium">{metricsData.jvm.version}</p>
                        </div>
                      </div>
                      
                      <div className="grid grid-cols-2 gap-4">
                        <div>
                          <h4 className="text-sm font-medium">JVM Vendor</h4>
                          <p className="text-lg font-medium">{metricsData.jvm.vendor}</p>
                        </div>
                        
                        <div>
                          <h4 className="text-sm font-medium">Uptime</h4>
                          <p className="text-lg font-medium">{formatTime(metricsData.jvm.uptime)}</p>
                        </div>
                      </div>
                      
                      <div>
                        <h4 className="text-sm font-medium mb-2">JVM Arguments</h4>
                        <div className="bg-muted p-2 rounded text-xs font-mono overflow-x-auto max-h-[150px] overflow-y-auto">
                          {metricsData.jvm.inputArguments.map((arg: string, index: number) => (
                            <div key={index}>{arg}</div>
                          ))}
                        </div>
                      </div>
                    </div>
                  ) : (
                    <p>No JVM information available</p>
                  )}
                </CardContent>
              </Card>
            </div>
          </TabsContent>
        </Tabs>
      </div>
    </DashboardLayout>
  )
}
