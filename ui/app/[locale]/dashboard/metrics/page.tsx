"use client"

import { useState, useEffect } from "react"
import { useTranslations } from 'next-intl'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Progress } from "@/components/ui/progress"
import { Badge } from "@/components/ui/badge"
import { RefreshCwIcon, AlertCircle, Activity, Cpu, HardDrive, Layers, Terminal, Server } from "lucide-react"
import { useToast } from "@/components/ui/use-toast"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { metricsApi, SystemMetrics, CpuMetrics, MemoryMetrics, ThreadMetrics, JvmMetrics, OsMetrics, HealthCheck } from "@/lib/api-client/metrics"

// Data card component for displaying metrics
interface DataCardProps {
  title: string
  value: string | number
  description?: string
  icon?: React.ReactNode
}

function DataCard({ title, value, description, icon }: DataCardProps) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
        <CardTitle className="text-sm font-medium">
          {title}
        </CardTitle>
        {icon}
      </CardHeader>
      <CardContent>
        <div className="text-2xl font-bold">{value}</div>
        {description && (
          <p className="text-xs text-muted-foreground">
            {description}
          </p>
        )}
      </CardContent>
    </Card>
  )
}

export default function MetricsPage() {
  const { toast } = useToast()
  const t = useTranslations('metrics')
  const common = useTranslations('common')

  const [metrics, setMetrics] = useState<SystemMetrics | null>(null)
  const [health, setHealth] = useState<HealthCheck | null>(null)
  const [activeTab, setActiveTab] = useState("overview")
  const [isLoading, setIsLoading] = useState(true)
  const [isRefreshing, setIsRefreshing] = useState(false)
  const [error, setError] = useState<Error | null>(null)

  // 加载指标数据
  const loadMetrics = async () => {
    try {
      setIsLoading(true)
      setError(null)

      // 调用 API
      const [metricsResponse, healthResponse] = await Promise.all([
        metricsApi.getMetrics(),
        metricsApi.getHealth()
      ])

      // 更新状态
      setMetrics(metricsResponse)
      setHealth(healthResponse)

    } catch (err) {
      console.error("Failed to load metrics:", err)
      setError(err instanceof Error ? err : new Error('Failed to load metrics'))
      toast({
        title: common('error'),
        description: err instanceof Error ? err.message : 'Unknown error',
        variant: "destructive"
      })
    } finally {
      setIsLoading(false)
      setIsRefreshing(false)
    }
  }

  // 初始加载
  useEffect(() => {
    loadMetrics()

    // 设置定时刷新（每 30 秒）
    const interval = setInterval(() => {
      loadMetrics()
    }, 30000)

    return () => clearInterval(interval)
  }, [])

  // 手动刷新
  const handleRefresh = () => {
    setIsRefreshing(true)
    loadMetrics()
  }

  // 格式化字节数
  const formatBytes = (bytes: number): string => {
    if (bytes === 0) return '0 B'

    const k = 1024
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB', 'PB']
    const i = Math.floor(Math.log(bytes) / Math.log(k))

    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
  }

  // 格式化时间
  const formatDuration = (ms: number): string => {
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

  // 渲染加载状态
  if (isLoading && !metrics) {
    return (
      <div className="flex justify-center py-8">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-4" data-testid="metrics-page">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">{t('title')}</h1>
          <p className="text-muted-foreground">
            {t('description')}
          </p>
        </div>
        <Button variant="outline" onClick={handleRefresh} disabled={isRefreshing}>
          <RefreshCwIcon className={`mr-2 h-4 w-4 ${isRefreshing ? 'animate-spin' : ''}`} />
          {common('refresh')}
        </Button>
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertTitle>{common('error')}</AlertTitle>
          <AlertDescription>{error.message}</AlertDescription>
        </Alert>
      )}

      {health && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-4">
          <Card className={`${health.status === 'UP' ? 'border-green-500' : health.status === 'DOWN' ? 'border-red-500' : 'border-yellow-500'}`}>
            <CardHeader className="pb-2">
              <CardTitle className="text-lg">{t('healthStatus')}</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="flex items-center">
                <Badge className={`${health.status === 'UP' ? 'bg-green-500' : health.status === 'DOWN' ? 'bg-red-500' : 'bg-yellow-500'} text-white`}>
                  {health.status === 'UP' ? t('healthy') : health.status === 'DOWN' ? t('unhealthy') : t('unknown')}
                </Badge>
                <span className="ml-2 text-sm text-muted-foreground">
                  {new Date(health.timestamp).toLocaleString()}
                </span>
              </div>
            </CardContent>
          </Card>

          {metrics && (
            <>
              <Card>
                <CardHeader className="pb-2">
                  <CardTitle className="text-lg">{t('uptime')}</CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">{formatDuration(metrics.uptime)}</div>
                </CardContent>
              </Card>

              <Card>
                <CardHeader className="pb-2">
                  <CardTitle className="text-lg">{t('requestsPerSecond')}</CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">{metrics.requestsPerSecond.toFixed(2)}</div>
                  <div className="text-sm text-muted-foreground">
                    {t('totalRequests')}: {metrics.requestCount.toLocaleString()}
                  </div>
                </CardContent>
              </Card>
            </>
          )}
        </div>
      )}

      {metrics && (
        <div className="grid gap-4 md:grid-cols-3">
          <DataCard
            title={t('cpuUsage')}
            value={`${(metrics.cpu.processCpuLoad * 100).toFixed(2)}%`}
            description={`${metrics.cpu.availableProcessors} ${t('coresAvailable')}`}
            icon={<Cpu className="h-4 w-4" />}
          />

          <DataCard
            title={t('memoryUsage')}
            value={`${((metrics.memory.heapMemoryUsed / metrics.memory.heapMemoryMax) * 100).toFixed(2)}%`}
            description={`${formatBytes(metrics.memory.heapMemoryUsed)} / ${formatBytes(metrics.memory.heapMemoryMax)}`}
            icon={<HardDrive className="h-4 w-4" />}
          />

          <DataCard
            title={t('threadCount')}
            value={metrics.threads.threadCount}
            description={`${t('peak')}: ${metrics.threads.peakThreadCount}`}
            icon={<Layers className="h-4 w-4" />}
          />
        </div>
      )}

      <Card className="mt-4">
        <CardHeader>
          <CardTitle>{t('systemOverview')}</CardTitle>
          <CardDescription>
            {t('systemOverviewDescription')}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {metrics ? (
            <Tabs value={activeTab} onValueChange={setActiveTab}>
              <TabsList className="grid grid-cols-5 mb-4">
                <TabsTrigger value="overview" className="flex items-center">
                  <Activity className="mr-2 h-4 w-4" />
                  {t('overview')}
                </TabsTrigger>
                <TabsTrigger value="cpu" className="flex items-center">
                  <Cpu className="mr-2 h-4 w-4" />
                  {t('cpu')}
                </TabsTrigger>
                <TabsTrigger value="memory" className="flex items-center">
                  <HardDrive className="mr-2 h-4 w-4" />
                  {t('memory')}
                </TabsTrigger>
                <TabsTrigger value="threads" className="flex items-center">
                  <Layers className="mr-2 h-4 w-4" />
                  {t('threads')}
                </TabsTrigger>
                <TabsTrigger value="jvm" className="flex items-center">
                  <Terminal className="mr-2 h-4 w-4" />
                  {t('jvm')}
                </TabsTrigger>
              </TabsList>

              <TabsContent value="overview" className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <h4 className="text-sm font-medium mb-1">{t('cpuUsage')}</h4>
                    <p className="text-sm">{(metrics.cpu.processCpuLoad * 100).toFixed(2)}%</p>
                    <Progress value={metrics.cpu.processCpuLoad * 100} className="mt-2" />
                  </div>

                  <div>
                    <h4 className="text-sm font-medium mb-1">{t('memoryUsage')}</h4>
                    <p className="text-sm">{((metrics.memory.heapMemoryUsed / metrics.memory.heapMemoryMax) * 100).toFixed(2)}%</p>
                    <Progress value={(metrics.memory.heapMemoryUsed / metrics.memory.heapMemoryMax) * 100} className="mt-2" />
                  </div>

                  <div>
                    <h4 className="text-sm font-medium mb-1">{t('activeConnections')}</h4>
                    <p className="text-sm">{metrics.activeConnections}</p>
                  </div>

                  <div>
                    <h4 className="text-sm font-medium mb-1">{t('errorRate')}</h4>
                    <p className="text-sm">{(metrics.errorRate * 100).toFixed(2)}%</p>
                  </div>
                </div>
              </TabsContent>

              <TabsContent value="cpu" className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <h4 className="text-sm font-medium mb-1">{t('processCpuLoad')}</h4>
                    <p className="text-sm">{(metrics.cpu.processCpuLoad * 100).toFixed(2)}%</p>
                    <Progress value={metrics.cpu.processCpuLoad * 100} className="mt-2" />
                  </div>

                  <div>
                    <h4 className="text-sm font-medium mb-1">{t('systemCpuLoad')}</h4>
                    <p className="text-sm">{(metrics.cpu.systemCpuLoad * 100).toFixed(2)}%</p>
                    <Progress value={metrics.cpu.systemCpuLoad * 100} className="mt-2" />
                  </div>

                  <div>
                    <h4 className="text-sm font-medium mb-1">{t('availableProcessors')}</h4>
                    <p className="text-sm">{metrics.cpu.availableProcessors}</p>
                  </div>

                  <div>
                    <h4 className="text-sm font-medium mb-1">{t('systemLoadAverage')}</h4>
                    <p className="text-sm">{metrics.cpu.systemLoadAverage.toFixed(2)}</p>
                  </div>
                </div>
              </TabsContent>

              <TabsContent value="memory" className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <h4 className="text-sm font-medium mb-1">{t('heapMemory')}</h4>
                    <p className="text-sm">{formatBytes(metrics.memory.heapMemoryUsed)} / {formatBytes(metrics.memory.heapMemoryMax)}</p>
                    <Progress value={(metrics.memory.heapMemoryUsed / metrics.memory.heapMemoryMax) * 100} className="mt-2" />
                  </div>

                  <div>
                    <h4 className="text-sm font-medium mb-1">{t('nonHeapMemory')}</h4>
                    <p className="text-sm">{formatBytes(metrics.memory.nonHeapMemoryUsed)}</p>
                  </div>

                  <div>
                    <h4 className="text-sm font-medium mb-1">{t('systemMemory')}</h4>
                    <p className="text-sm">{formatBytes(metrics.memory.systemMemoryUsed)} / {formatBytes(metrics.memory.systemMemoryTotal)}</p>
                    <Progress value={(metrics.memory.systemMemoryUsed / metrics.memory.systemMemoryTotal) * 100} className="mt-2" />
                  </div>
                </div>
              </TabsContent>

              <TabsContent value="threads" className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <h4 className="text-sm font-medium mb-1">{t('threadCount')}</h4>
                    <p className="text-sm">{metrics.threads.threadCount}</p>
                  </div>

                  <div>
                    <h4 className="text-sm font-medium mb-1">{t('daemonThreads')}</h4>
                    <p className="text-sm">{metrics.threads.daemonThreadCount}</p>
                  </div>

                  <div>
                    <h4 className="text-sm font-medium mb-1">{t('peakThreadCount')}</h4>
                    <p className="text-sm">{metrics.threads.peakThreadCount}</p>
                  </div>

                  <div>
                    <h4 className="text-sm font-medium mb-1">{t('deadlockedThreads')}</h4>
                    <p className="text-sm">{metrics.threads.deadlockedThreads}</p>
                  </div>
                </div>

                {metrics.threads.threadStates && metrics.threads.threadStates.length > 0 && (
                  <div>
                    <h4 className="text-sm font-medium mb-2">{t('threadStates')}</h4>
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
                      {metrics.threads.threadStates.map((state) => (
                        <div key={state.state} className="bg-muted p-2 rounded">
                          <div className="text-xs font-medium">{state.state}</div>
                          <div className="text-sm">{state.count}</div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </TabsContent>

              <TabsContent value="jvm" className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <h4 className="text-sm font-medium mb-1">{t('jvmInfo')}</h4>
                    <div className="space-y-1">
                      <div className="flex justify-between">
                        <span className="text-xs text-muted-foreground">{t('jvmName')}:</span>
                        <span className="text-xs">{metrics.jvm.jvmName}</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-xs text-muted-foreground">{t('jvmVersion')}:</span>
                        <span className="text-xs">{metrics.jvm.jvmVersion}</span>
                      </div>
                      <div className="flex justify-between">
                        <span className="text-xs text-muted-foreground">{t('jvmVendor')}:</span>
                        <span className="text-xs">{metrics.jvm.jvmVendor}</span>
                      </div>
                    </div>
                  </div>

                  <div>
                    <h4 className="text-sm font-medium mb-1">{t('jvmUptime')}</h4>
                    <p className="text-sm">{formatDuration(metrics.jvm.uptime)}</p>
                    <div className="text-xs text-muted-foreground mt-1">
                      {t('startTime')}: {new Date(metrics.jvm.startTime).toLocaleString()}
                    </div>
                  </div>
                </div>

                {metrics.jvm.gcCollectors && metrics.jvm.gcCollectors.length > 0 && (
                  <div>
                    <h4 className="text-sm font-medium mb-2">{t('gcCollectors')}</h4>
                    <div className="space-y-2">
                      {metrics.jvm.gcCollectors.map((collector) => (
                        <div key={collector.name} className="bg-muted p-2 rounded">
                          <div className="text-xs font-medium">{collector.name}</div>
                          <div className="flex justify-between text-xs">
                            <span>{t('collectionCount')}: {collector.collectionCount}</span>
                            <span>{t('collectionTime')}: {collector.collectionTime} ms</span>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </TabsContent>
            </Tabs>
          ) : (
            <div className="flex justify-center py-4">
              <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-primary"></div>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
