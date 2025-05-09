"use client"

import { useState, useEffect } from "react"
import { useRouter } from "next/navigation"
import { useTranslations, useLocale } from 'next-intl'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Progress } from "@/components/ui/progress"
import { AlertCircle, RefreshCw, Cpu, Memory, HardDrive, Activity, Server } from "lucide-react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { useToast } from "@/components/ui/use-toast"

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
  const router = useRouter()
  const locale = useLocale()
  const { toast } = useToast()
  const t = useTranslations('metrics')
  const common = useTranslations('common')

  const [activeTab, setActiveTab] = useState("overview")
  const [autoRefresh, setAutoRefresh] = useState(false)
  const [refreshInterval, setRefreshInterval] = useState<NodeJS.Timeout | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [isRefetching, setIsRefetching] = useState(false)
  const [error, setError] = useState<Error | null>(null)
  const [metricsData, setMetricsData] = useState<any>(null)
  const [healthData, setHealthData] = useState<any>(null)

  // Format bytes to human-readable format
  const formatBytes = (bytes: number, decimals = 2) => {
    if (bytes === 0) return '0 Bytes'

    const k = 1024
    const dm = decimals < 0 ? 0 : decimals
    const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB']

    const i = Math.floor(Math.log(bytes) / Math.log(k))

    return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i]
  }

  // Fetch metrics data
  const fetchMetrics = async () => {
    setIsRefetching(true)
    try {
      const response = await fetch('/api/metrics')
      if (!response.ok) {
        throw new Error(`API error: ${response.status}`)
      }
      const data = await response.json()
      setMetricsData(data)
      setError(null)
    } catch (err) {
      setError(err instanceof Error ? err : new Error('Unknown error'))
      toast({
        title: common('error'),
        description: err instanceof Error ? err.message : 'Unknown error',
        variant: "destructive"
      })
    } finally {
      setIsRefetching(false)
      setIsLoading(false)
    }
  }

  // Fetch health data
  const fetchHealth = async () => {
    try {
      const response = await fetch('/api/health')
      if (!response.ok) {
        throw new Error(`API error: ${response.status}`)
      }
      const data = await response.json()
      setHealthData(data)
    } catch (err) {
      console.error('Error fetching health check:', err)
    }
  }

  // Fetch data on component mount
  useEffect(() => {
    fetchMetrics()
    fetchHealth()
  }, [])

  // Set up auto-refresh
  useEffect(() => {
    if (autoRefresh) {
      const interval = setInterval(() => {
        fetchMetrics()
        fetchHealth()
      }, 5000) // Refresh every 5 seconds

      setRefreshInterval(interval)

      return () => {
        if (interval) clearInterval(interval)
      }
    } else {
      if (refreshInterval) {
        clearInterval(refreshInterval)
        setRefreshInterval(null)
      }
    }
  }, [autoRefresh])

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">{t('title')}</h1>
          <p className="text-muted-foreground">
            {t('description')}
          </p>
        </div>
        <div className="flex gap-2">
          <Button
            variant="outline"
            size="icon"
            onClick={() => {
              fetchMetrics()
              fetchHealth()
            }}
            disabled={isRefetching}
          >
            <RefreshCw className={`h-4 w-4 ${isRefetching ? 'animate-spin' : ''}`} />
          </Button>
          <Button
            variant={autoRefresh ? "default" : "outline"}
            onClick={() => setAutoRefresh(!autoRefresh)}
          >
            {autoRefresh ? t('autoRefreshOn') : t('autoRefreshOff')}
          </Button>
        </div>
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertTitle>{common('error')}</AlertTitle>
          <AlertDescription>{error.message}</AlertDescription>
        </Alert>
      )}

      {healthData && (
        <Card className={healthData.status === 'UP' ? 'bg-green-50 border-green-200' : 'bg-red-50 border-red-200'}>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium">{t('systemStatus')}</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex items-center">
              <div className={`h-3 w-3 rounded-full mr-2 ${healthData.status === 'UP' ? 'bg-green-500' : 'bg-red-500'}`}></div>
              <span className="font-medium">{healthData.status === 'UP' ? t('systemUp') : t('systemDown')}</span>
            </div>
            <p className="text-xs text-muted-foreground mt-1">
              {t('lastUpdated')}: {new Date().toLocaleTimeString()}
            </p>
          </CardContent>
        </Card>
      )}

      <Tabs defaultValue="overview" value={activeTab} onValueChange={setActiveTab}>
        <TabsList className="grid w-full grid-cols-5">
          <TabsTrigger value="overview" className="flex items-center gap-2">
            <Activity className="h-4 w-4" />
            {t('overview')}
          </TabsTrigger>
          <TabsTrigger value="cpu" className="flex items-center gap-2">
            <Cpu className="h-4 w-4" />
            {t('cpu')}
          </TabsTrigger>
          <TabsTrigger value="memory" className="flex items-center gap-2">
            <Memory className="h-4 w-4" />
            {t('memory')}
          </TabsTrigger>
          <TabsTrigger value="jvm" className="flex items-center gap-2">
            <Server className="h-4 w-4" />
            {t('jvm')}
          </TabsTrigger>
          <TabsTrigger value="threads" className="flex items-center gap-2">
            <HardDrive className="h-4 w-4" />
            {t('threads')}
          </TabsTrigger>
        </TabsList>

        <TabsContent value="overview" className="space-y-4 mt-4">
          <div className="grid gap-4 md:grid-cols-3">
            {isLoading ? (
              <>
                <Card className="p-6">
                  <div className="animate-pulse h-20"></div>
                </Card>
                <Card className="p-6">
                  <div className="animate-pulse h-20"></div>
                </Card>
                <Card className="p-6">
                  <div className="animate-pulse h-20"></div>
                </Card>
              </>
            ) : metricsData ? (
              <>
                <DataCard
                  title={t('cpuUsage')}
                  value={`${(metricsData.cpu?.processCpuLoad * 100).toFixed(2)}%`}
                  description={`${metricsData.cpu?.cores} ${t('coresAvailable')}`}
                  icon={<Cpu className="h-4 w-4" />}
                />

                <DataCard
                  title={t('memoryUsage')}
                  value={formatBytes(metricsData.memory?.heap?.used || 0)}
                  description={`${((metricsData.memory?.heap?.used / metricsData.memory?.heap?.max) * 100).toFixed(2)}% ${t('of')} ${formatBytes(metricsData.memory?.heap?.max || 0)}`}
                  icon={<Memory className="h-4 w-4" />}
                />

                <DataCard
                  title={t('threadCount')}
                  value={metricsData.threads?.count || 0}
                  description={`${t('peak')}: ${metricsData.threads?.peakCount || 0}`}
                  icon={<HardDrive className="h-4 w-4" />}
                />
              </>
            ) : (
              <div className="col-span-3 text-center py-8 text-muted-foreground">
                {t('noData')}
              </div>
            )}
          </div>

          {metricsData && (
            <Card>
              <CardHeader>
                <CardTitle>{t('systemOverview')}</CardTitle>
                <CardDescription>
                  {t('systemOverviewDescription')}
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="text-sm font-medium">{t('cpuUsage')}</span>
                    <span className="text-sm text-muted-foreground">
                      {(metricsData.cpu?.processCpuLoad * 100).toFixed(2)}%
                    </span>
                  </div>
                  <Progress value={metricsData.cpu?.processCpuLoad * 100} />
                </div>

                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="text-sm font-medium">{t('memoryUsage')}</span>
                    <span className="text-sm text-muted-foreground">
                      {((metricsData.memory?.heap?.used / metricsData.memory?.heap?.max) * 100).toFixed(2)}%
                    </span>
                  </div>
                  <Progress value={(metricsData.memory?.heap?.used / metricsData.memory?.heap?.max) * 100} />
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <h4 className="text-sm font-medium mb-1">{t('osInfo')}</h4>
                    <p className="text-sm text-muted-foreground">
                      {metricsData.os?.name} {metricsData.os?.version} ({metricsData.os?.arch})
                    </p>
                  </div>

                  <div>
                    <h4 className="text-sm font-medium mb-1">{t('jvmInfo')}</h4>
                    <p className="text-sm text-muted-foreground">
                      {metricsData.jvm?.version} ({metricsData.jvm?.vendor})
                    </p>
                  </div>
                </div>
              </CardContent>
            </Card>
          )}
        </TabsContent>

        <TabsContent value="cpu" className="space-y-4 mt-4">
          {isLoading ? (
            <Card className="p-6">
              <div className="animate-pulse h-40"></div>
            </Card>
          ) : metricsData?.cpu ? (
            <Card>
              <CardHeader>
                <CardTitle>{t('cpuMetrics')}</CardTitle>
                <CardDescription>
                  {t('cpuMetricsDescription')}
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="text-sm font-medium">{t('processCpuLoad')}</span>
                    <span className="text-sm text-muted-foreground">
                      {(metricsData.cpu.processCpuLoad * 100).toFixed(2)}%
                    </span>
                  </div>
                  <Progress value={metricsData.cpu.processCpuLoad * 100} />
                </div>

                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="text-sm font-medium">{t('systemCpuLoad')}</span>
                    <span className="text-sm text-muted-foreground">
                      {metricsData.cpu.systemLoad > 0 ? (metricsData.cpu.systemLoad).toFixed(2) : 'N/A'}
                    </span>
                  </div>
                  {metricsData.cpu.systemLoad > 0 && (
                    <Progress value={(metricsData.cpu.systemLoad / metricsData.cpu.cores) * 100} />
                  )}
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <DataCard
                    title={t('availableCores')}
                    value={metricsData.cpu.cores}
                  />

                  <DataCard
                    title={t('processCpuTime')}
                    value={`${(metricsData.cpu.processCpuTime / 1000000000).toFixed(2)} ${t('seconds')}`}
                  />
                </div>
              </CardContent>
            </Card>
          ) : (
            <div className="text-center py-8 text-muted-foreground">
              {t('noCpuData')}
            </div>
          )}
        </TabsContent>

        <TabsContent value="memory" className="space-y-4 mt-4">
          {isLoading ? (
            <Card className="p-6">
              <div className="animate-pulse h-40"></div>
            </Card>
          ) : metricsData?.memory ? (
            <>
              <Card>
                <CardHeader>
                  <CardTitle>{t('heapMemory')}</CardTitle>
                  <CardDescription>
                    {t('heapMemoryDescription')}
                  </CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div className="space-y-2">
                    <div className="flex items-center justify-between">
                      <span className="text-sm font-medium">{t('heapUsage')}</span>
                      <span className="text-sm text-muted-foreground">
                        {((metricsData.memory.heap.used / metricsData.memory.heap.max) * 100).toFixed(2)}%
                      </span>
                    </div>
                    <Progress value={(metricsData.memory.heap.used / metricsData.memory.heap.max) * 100} />
                  </div>

                  <div className="grid grid-cols-3 gap-4">
                    <DataCard
                      title={t('used')}
                      value={formatBytes(metricsData.memory.heap.used)}
                    />

                    <DataCard
                      title={t('committed')}
                      value={formatBytes(metricsData.memory.heap.committed)}
                    />

                    <DataCard
                      title={t('max')}
                      value={formatBytes(metricsData.memory.heap.max)}
                    />
                  </div>
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle>{t('nonHeapMemory')}</CardTitle>
                  <CardDescription>
                    {t('nonHeapMemoryDescription')}
                  </CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div className="grid grid-cols-2 gap-4">
                    <DataCard
                      title={t('used')}
                      value={formatBytes(metricsData.memory.nonHeap.used)}
                    />

                    <DataCard
                      title={t('committed')}
                      value={formatBytes(metricsData.memory.nonHeap.committed)}
                    />
                  </div>
                </CardContent>
              </Card>
            </>
          ) : (
            <div className="text-center py-8 text-muted-foreground">
              {t('noMemoryData')}
            </div>
          )}
        </TabsContent>

        <TabsContent value="jvm" className="space-y-4 mt-4">
          {isLoading ? (
            <Card className="p-6">
              <div className="animate-pulse h-40"></div>
            </Card>
          ) : metricsData?.jvm ? (
            <Card>
              <CardHeader>
                <CardTitle>{t('jvmInfo')}</CardTitle>
                <CardDescription>
                  {t('jvmInfoDescription')}
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <h4 className="text-sm font-medium mb-1">{t('jvmName')}</h4>
                    <p className="text-sm">{metricsData.jvm.name}</p>
                  </div>

                  <div>
                    <h4 className="text-sm font-medium mb-1">{t('jvmVendor')}</h4>
                    <p className="text-sm">{metricsData.jvm.vendor}</p>
                  </div>

                  <div>
                    <h4 className="text-sm font-medium mb-1">{t('jvmVersion')}</h4>
                    <p className="text-sm">{metricsData.jvm.version}</p>
                  </div>

                  <div>
                    <h4 className="text-sm font-medium mb-1">{t('uptime')}</h4>
                    <p className="text-sm">{Math.floor(metricsData.jvm.uptime / (1000 * 60 * 60 * 24))} {t('days')}, {Math.floor((metricsData.jvm.uptime % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60))} {t('hours')}</p>
                  </div>
                </div>

                <div>
                  <h4 className="text-sm font-medium mb-1">{t('systemProperties')}</h4>
                  <div className="bg-muted p-2 rounded-md text-xs overflow-auto max-h-40">
                    <pre>
                      {JSON.stringify(metricsData.jvm.systemProperties || {}, null, 2)}
                    </pre>
                  </div>
                </div>
              </CardContent>
            </Card>
          ) : (
            <div className="text-center py-8 text-muted-foreground">
              {t('noJvmData')}
            </div>
          )}
        </TabsContent>

        <TabsContent value="threads" className="space-y-4 mt-4">
          {isLoading ? (
            <Card className="p-6">
              <div className="animate-pulse h-40"></div>
            </Card>
          ) : metricsData?.threads ? (
            <Card>
              <CardHeader>
                <CardTitle>{t('threadMetrics')}</CardTitle>
                <CardDescription>
                  {t('threadMetricsDescription')}
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <DataCard
                    title={t('currentThreads')}
                    value={metricsData.threads.count}
                  />

                  <DataCard
                    title={t('peakThreads')}
                    value={metricsData.threads.peakCount}
                  />

                  <DataCard
                    title={t('daemonThreads')}
                    value={metricsData.threads.daemonCount}
                  />

                  <DataCard
                    title={t('totalStarted')}
                    value={metricsData.threads.totalStarted}
                  />
                </div>

                {metricsData.threads.threadDetails && (
                  <div>
                    <h4 className="text-sm font-medium mb-2">{t('threadStates')}</h4>
                    <div className="space-y-2">
                      {Object.entries(metricsData.threads.threadDetails).map(([state, count]) => (
                        <div key={state} className="flex items-center justify-between">
                          <span className="text-sm">{state}</span>
                          <span className="text-sm font-medium">{count}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </CardContent>
            </Card>
          ) : (
            <div className="text-center py-8 text-muted-foreground">
              {t('noThreadData')}
            </div>
          )}
        </TabsContent>
      </Tabs>
    </div>
  )
}
