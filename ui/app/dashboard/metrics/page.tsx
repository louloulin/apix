"use client"

import { useState, useEffect } from "react"
import { DashboardLayout } from "@/components/layout/dashboard-layout"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Progress } from "@/components/ui/progress"
import { AlertCircle, RefreshCw, Cpu, Memory, HardDrive, Activity, Server } from "lucide-react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { useToast } from "@/components/ui/use-toast"
import { useApiData } from "@/lib/hooks/use-api-data"
import { metricsApi } from "@/lib/api-client"
import { DataCard } from "@/components/ui/data-card"

export default function MetricsPage() {
  const { toast } = useToast()
  const [activeTab, setActiveTab] = useState("overview")
  const [autoRefresh, setAutoRefresh] = useState(false)
  const [refreshInterval, setRefreshInterval] = useState<NodeJS.Timeout | null>(null)
  
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
        toast({
          title: "Error fetching metrics",
          description: err.message,
          variant: "destructive"
        })
      }
    }
  )
  
  // Fetch health data
  const { 
    data: healthData, 
    refetch: refetchHealth
  } = useApiData(
    () => metricsApi.getHealth(),
    {
      onError: (err) => {
        toast({
          title: "Error fetching health check",
          description: err.message,
          variant: "destructive"
        })
      }
    }
  )
  
  // Handle auto-refresh
  useEffect(() => {
    if (autoRefresh) {
      const interval = setInterval(() => {
        refetch()
        refetchHealth()
      }, 5000)
      
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
  }, [autoRefresh, refetch, refetchHealth])
  
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
            <h1 className="text-3xl font-bold">System Metrics</h1>
            <p className="text-muted-foreground">
              Monitor system performance and health
            </p>
          </div>
          <div className="flex gap-2">
            <Button 
              variant="outline" 
              size="icon" 
              onClick={() => {
                refetch()
                refetchHealth()
              }}
              disabled={isRefetching}
            >
              <RefreshCw className={`h-4 w-4 ${isRefetching ? 'animate-spin' : ''}`} />
            </Button>
            <Button 
              variant={autoRefresh ? "default" : "outline"}
              onClick={() => setAutoRefresh(!autoRefresh)}
            >
              {autoRefresh ? "Auto-Refresh: On" : "Auto-Refresh: Off"}
            </Button>
          </div>
        </div>

        {error && (
          <Alert variant="destructive">
            <AlertCircle className="h-4 w-4" />
            <AlertTitle>Error</AlertTitle>
            <AlertDescription>{error.message}</AlertDescription>
          </Alert>
        )}
        
        {healthData && (
          <Alert variant={healthData.status === "UP" ? "default" : "destructive"}>
            <Activity className="h-4 w-4" />
            <AlertTitle>System Status: {healthData.status}</AlertTitle>
            <AlertDescription>
              {healthData.status === "UP" 
                ? "All systems operational" 
                : "Some systems are experiencing issues"}
            </AlertDescription>
          </Alert>
        )}

        <Tabs defaultValue="overview" value={activeTab} onValueChange={setActiveTab}>
          <TabsList className="grid w-full grid-cols-5">
            <TabsTrigger value="overview" className="flex items-center gap-2">
              <Activity className="h-4 w-4" />
              Overview
            </TabsTrigger>
            <TabsTrigger value="cpu" className="flex items-center gap-2">
              <Cpu className="h-4 w-4" />
              CPU
            </TabsTrigger>
            <TabsTrigger value="memory" className="flex items-center gap-2">
              <Memory className="h-4 w-4" />
              Memory
            </TabsTrigger>
            <TabsTrigger value="jvm" className="flex items-center gap-2">
              <Server className="h-4 w-4" />
              JVM
            </TabsTrigger>
            <TabsTrigger value="threads" className="flex items-center gap-2">
              <HardDrive className="h-4 w-4" />
              Threads
            </TabsTrigger>
          </TabsList>
          
          <TabsContent value="overview">
            <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
              {isLoading ? (
                Array(4).fill(0).map((_, i) => (
                  <Card key={i} className="opacity-70">
                    <CardHeader className="pb-2">
                      <div className="h-5 w-24 bg-muted rounded animate-pulse"></div>
                    </CardHeader>
                    <CardContent>
                      <div className="h-8 w-32 bg-muted rounded animate-pulse mb-2"></div>
                      <div className="h-4 w-full bg-muted rounded animate-pulse"></div>
                    </CardContent>
                  </Card>
                ))
              ) : metricsData ? (
                <>
                  <DataCard
                    title="CPU Usage"
                    value={`${(metricsData.cpu?.processCpuLoad * 100).toFixed(2)}%`}
                    description={`${metricsData.cpu?.cores} cores available`}
                    icon={<Cpu className="h-4 w-4" />}
                  />
                  
                  <DataCard
                    title="Memory Usage"
                    value={formatBytes(metricsData.memory?.heap?.used || 0)}
                    description={`${((metricsData.memory?.heap?.used / metricsData.memory?.heap?.max) * 100).toFixed(2)}% of ${formatBytes(metricsData.memory?.heap?.max || 0)}`}
                    icon={<Memory className="h-4 w-4" />}
                  />
                  
                  <DataCard
                    title="Thread Count"
                    value={metricsData.threads?.count || 0}
                    description={`Peak: ${metricsData.threads?.peakCount || 0}`}
                    icon={<HardDrive className="h-4 w-4" />}
                  />
                  
                  <DataCard
                    title="Uptime"
                    value={formatTime(metricsData.jvm?.uptime || 0)}
                    description={`Started: ${new Date(metricsData.jvm?.startTime || 0).toLocaleString()}`}
                    icon={<Server className="h-4 w-4" />}
                  />
                </>
              ) : (
                <p>No metrics data available</p>
              )}
            </div>
          </TabsContent>
          
          <TabsContent value="cpu">
            <Card>
              <CardHeader>
                <CardTitle>CPU Metrics</CardTitle>
                <CardDescription>
                  Detailed CPU usage information
                </CardDescription>
              </CardHeader>
              <CardContent>
                {isLoading ? (
                  <div className="space-y-4">
                    <div className="h-8 w-full bg-muted rounded animate-pulse"></div>
                    <div className="h-8 w-full bg-muted rounded animate-pulse"></div>
                    <div className="h-8 w-full bg-muted rounded animate-pulse"></div>
                  </div>
                ) : metricsData?.cpu ? (
                  <div className="space-y-4">
                    <div className="space-y-2">
                      <div className="flex justify-between">
                        <span>Process CPU Load</span>
                        <span>{(metricsData.cpu.processCpuLoad * 100).toFixed(2)}%</span>
                      </div>
                      <Progress value={metricsData.cpu.processCpuLoad * 100} />
                    </div>
                    
                    <div className="space-y-2">
                      <div className="flex justify-between">
                        <span>System Load Average</span>
                        <span>{metricsData.cpu.systemLoad.toFixed(2)}</span>
                      </div>
                      <Progress value={(metricsData.cpu.systemLoad / metricsData.cpu.cores) * 100} />
                    </div>
                    
                    <div className="grid grid-cols-2 gap-4">
                      <div>
                        <h4 className="text-sm font-medium">Available Processors</h4>
                        <p className="text-2xl font-bold">{metricsData.cpu.cores}</p>
                      </div>
                      
                      <div>
                        <h4 className="text-sm font-medium">Process CPU Time</h4>
                        <p className="text-2xl font-bold">{(metricsData.cpu.processCpuTime / 1000000000).toFixed(2)}s</p>
                      </div>
                    </div>
                  </div>
                ) : (
                  <p>No CPU metrics available</p>
                )}
              </CardContent>
            </Card>
          </TabsContent>
          
          <TabsContent value="memory">
            <Card>
              <CardHeader>
                <CardTitle>Memory Metrics</CardTitle>
                <CardDescription>
                  Detailed memory usage information
                </CardDescription>
              </CardHeader>
              <CardContent>
                {isLoading ? (
                  <div className="space-y-4">
                    <div className="h-8 w-full bg-muted rounded animate-pulse"></div>
                    <div className="h-8 w-full bg-muted rounded animate-pulse"></div>
                    <div className="h-8 w-full bg-muted rounded animate-pulse"></div>
                  </div>
                ) : metricsData?.memory ? (
                  <div className="space-y-4">
                    <div className="space-y-2">
                      <div className="flex justify-between">
                        <span>Heap Memory Usage</span>
                        <span>{((metricsData.memory.heap.used / metricsData.memory.heap.max) * 100).toFixed(2)}%</span>
                      </div>
                      <Progress value={(metricsData.memory.heap.used / metricsData.memory.heap.max) * 100} />
                      <div className="flex justify-between text-xs text-muted-foreground">
                        <span>Used: {formatBytes(metricsData.memory.heap.used)}</span>
                        <span>Max: {formatBytes(metricsData.memory.heap.max)}</span>
                      </div>
                    </div>
                    
                    <div className="space-y-2">
                      <div className="flex justify-between">
                        <span>Non-Heap Memory Usage</span>
                        <span>{formatBytes(metricsData.memory.nonHeap.used)}</span>
                      </div>
                      <Progress 
                        value={
                          metricsData.memory.nonHeap.max > 0 
                            ? (metricsData.memory.nonHeap.used / metricsData.memory.nonHeap.max) * 100 
                            : (metricsData.memory.nonHeap.used / metricsData.memory.nonHeap.committed) * 100
                        } 
                      />
                      <div className="flex justify-between text-xs text-muted-foreground">
                        <span>Used: {formatBytes(metricsData.memory.nonHeap.used)}</span>
                        <span>
                          {metricsData.memory.nonHeap.max > 0 
                            ? `Max: ${formatBytes(metricsData.memory.nonHeap.max)}` 
                            : `Committed: ${formatBytes(metricsData.memory.nonHeap.committed)}`
                          }
                        </span>
                      </div>
                    </div>
                    
                    <div className="grid grid-cols-2 gap-4">
                      <div>
                        <h4 className="text-sm font-medium">Total Memory</h4>
                        <p className="text-2xl font-bold">{formatBytes(metricsData.memory.total.committed)}</p>
                        <p className="text-xs text-muted-foreground">Committed</p>
                      </div>
                      
                      <div>
                        <h4 className="text-sm font-medium">Total Used</h4>
                        <p className="text-2xl font-bold">{formatBytes(metricsData.memory.total.used)}</p>
                        <p className="text-xs text-muted-foreground">
                          {((metricsData.memory.total.used / metricsData.memory.total.committed) * 100).toFixed(2)}% of committed
                        </p>
                      </div>
                    </div>
                  </div>
                ) : (
                  <p>No memory metrics available</p>
                )}
              </CardContent>
            </Card>
          </TabsContent>
          
          <TabsContent value="jvm">
            <Card>
              <CardHeader>
                <CardTitle>JVM Metrics</CardTitle>
                <CardDescription>
                  Java Virtual Machine information
                </CardDescription>
              </CardHeader>
              <CardContent>
                {isLoading ? (
                  <div className="space-y-4">
                    <div className="h-8 w-full bg-muted rounded animate-pulse"></div>
                    <div className="h-8 w-full bg-muted rounded animate-pulse"></div>
                    <div className="h-8 w-full bg-muted rounded animate-pulse"></div>
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
                      <div className="bg-muted p-2 rounded text-xs font-mono overflow-x-auto">
                        {metricsData.jvm.inputArguments.map((arg: string, index: number) => (
                          <div key={index}>{arg}</div>
                        ))}
                      </div>
                    </div>
                  </div>
                ) : (
                  <p>No JVM metrics available</p>
                )}
              </CardContent>
            </Card>
          </TabsContent>
          
          <TabsContent value="threads">
            <Card>
              <CardHeader>
                <CardTitle>Thread Metrics</CardTitle>
                <CardDescription>
                  Thread usage information
                </CardDescription>
              </CardHeader>
              <CardContent>
                {isLoading ? (
                  <div className="space-y-4">
                    <div className="h-8 w-full bg-muted rounded animate-pulse"></div>
                    <div className="h-8 w-full bg-muted rounded animate-pulse"></div>
                    <div className="h-8 w-full bg-muted rounded animate-pulse"></div>
                  </div>
                ) : metricsData?.threads ? (
                  <div className="space-y-4">
                    <div className="grid grid-cols-2 gap-4">
                      <div>
                        <h4 className="text-sm font-medium">Current Thread Count</h4>
                        <p className="text-2xl font-bold">{metricsData.threads.count}</p>
                      </div>
                      
                      <div>
                        <h4 className="text-sm font-medium">Peak Thread Count</h4>
                        <p className="text-2xl font-bold">{metricsData.threads.peakCount}</p>
                      </div>
                    </div>
                    
                    <div className="grid grid-cols-2 gap-4">
                      <div>
                        <h4 className="text-sm font-medium">Daemon Thread Count</h4>
                        <p className="text-2xl font-bold">{metricsData.threads.daemonCount}</p>
                      </div>
                      
                      <div>
                        <h4 className="text-sm font-medium">Total Started Threads</h4>
                        <p className="text-2xl font-bold">{metricsData.threads.totalStarted}</p>
                      </div>
                    </div>
                    
                    <div className="space-y-2">
                      <div className="flex justify-between">
                        <span>Thread Utilization</span>
                        <span>{((metricsData.threads.count / metricsData.threads.peakCount) * 100).toFixed(2)}%</span>
                      </div>
                      <Progress value={(metricsData.threads.count / metricsData.threads.peakCount) * 100} />
                      <div className="flex justify-between text-xs text-muted-foreground">
                        <span>Current: {metricsData.threads.count}</span>
                        <span>Peak: {metricsData.threads.peakCount}</span>
                      </div>
                    </div>
                  </div>
                ) : (
                  <p>No thread metrics available</p>
                )}
              </CardContent>
            </Card>
          </TabsContent>
        </Tabs>
      </div>
    </DashboardLayout>
  )
}
