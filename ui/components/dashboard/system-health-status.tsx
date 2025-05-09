"use client"

import { useState, useEffect } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Progress } from "@/components/ui/progress"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { RefreshCw, CheckCircle, AlertTriangle, XCircle, Server, Cpu, Memory, HardDrive } from "lucide-react"
import { useApiData } from "@/lib/hooks/use-api-data"
import { metricsApi } from "@/lib/api-client"

interface SystemHealthStatusProps {
  title?: string
  description?: string
}

export function SystemHealthStatus({
  title = "System Health",
  description = "Current health status of system components"
}: SystemHealthStatusProps) {
  const [autoRefresh, setAutoRefresh] = useState(false)
  
  // Fetch health data
  const { 
    data: healthData, 
    isLoading, 
    error, 
    refetch,
    isRefetching
  } = useApiData(
    () => metricsApi.getHealth(),
    {
      onError: (err) => {
        console.error("Error fetching health data:", err)
      }
    }
  )
  
  // Fetch metrics data
  const { 
    data: metricsData, 
    refetch: refetchMetrics
  } = useApiData(
    () => metricsApi.getMetrics(),
    {
      onError: (err) => {
        console.error("Error fetching metrics data:", err)
      }
    }
  )
  
  // Auto-refresh
  useEffect(() => {
    if (!autoRefresh) return
    
    const interval = setInterval(() => {
      refetch()
      refetchMetrics()
    }, 10000) // Refresh every 10 seconds
    
    return () => clearInterval(interval)
  }, [autoRefresh, refetch, refetchMetrics])
  
  // Get status color
  const getStatusColor = (status: string) => {
    switch (status) {
      case "UP":
        return "bg-green-500"
      case "WARNING":
        return "bg-yellow-500"
      case "DOWN":
        return "bg-red-500"
      default:
        return "bg-gray-500"
    }
  }
  
  // Get status icon
  const getStatusIcon = (status: string) => {
    switch (status) {
      case "UP":
        return <CheckCircle className="h-5 w-5 text-green-500" />
      case "WARNING":
        return <AlertTriangle className="h-5 w-5 text-yellow-500" />
      case "DOWN":
        return <XCircle className="h-5 w-5 text-red-500" />
      default:
        return <AlertTriangle className="h-5 w-5 text-gray-500" />
    }
  }
  
  // Format uptime
  const formatUptime = (ms: number) => {
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
  
  // Format bytes
  const formatBytes = (bytes: number) => {
    if (bytes === 0) return '0 Bytes'
    
    const k = 1024
    const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB']
    const i = Math.floor(Math.log(bytes) / Math.log(k))
    
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
  }
  
  // Calculate overall status
  const calculateOverallStatus = () => {
    if (!healthData?.checks) return "UNKNOWN"
    
    if (healthData.checks.some(check => check.status === "DOWN")) {
      return "DOWN"
    } else if (healthData.checks.some(check => check.status === "WARNING")) {
      return "WARNING"
    } else {
      return "UP"
    }
  }
  
  const overallStatus = calculateOverallStatus()
  
  // Get memory usage percentage
  const memoryUsagePercent = metricsData?.memory?.heap?.usagePercent || 0
  
  // Get CPU usage percentage
  const cpuUsagePercent = metricsData?.cpu?.processCpuLoad ? metricsData.cpu.processCpuLoad * 100 : 0
  
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between pb-2">
        <div className="space-y-0.5">
          <CardTitle>{title}</CardTitle>
          <CardDescription>{description}</CardDescription>
        </div>
        <Button 
          variant="outline" 
          size="icon" 
          onClick={() => {
            refetch()
            refetchMetrics()
          }}
          disabled={isRefetching}
        >
          <RefreshCw className={`h-4 w-4 ${isRefetching ? 'animate-spin' : ''}`} />
        </Button>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <div className="flex h-[200px] items-center justify-center">
            <div className="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent"></div>
          </div>
        ) : error ? (
          <div className="flex h-[200px] flex-col items-center justify-center text-center">
            <XCircle className="h-10 w-10 text-red-500 mb-2" />
            <h3 className="text-lg font-medium">Unable to fetch health data</h3>
            <p className="text-sm text-muted-foreground">Please try again later</p>
          </div>
        ) : (
          <div className="space-y-4">
            {/* Overall Status */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                {getStatusIcon(overallStatus)}
                <span className="font-medium">Overall Status</span>
              </div>
              <Badge 
                variant={overallStatus === "UP" ? "default" : overallStatus === "WARNING" ? "outline" : "destructive"}
              >
                {overallStatus}
              </Badge>
            </div>
            
            {/* Uptime */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Server className="h-5 w-5 text-blue-500" />
                <span className="font-medium">Uptime</span>
              </div>
              <span>{formatUptime(healthData?.uptime || 0)}</span>
            </div>
            
            {/* Memory Usage */}
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Memory className="h-5 w-5 text-purple-500" />
                  <span className="font-medium">Memory Usage</span>
                </div>
                <span className={memoryUsagePercent > 80 ? "text-red-500" : memoryUsagePercent > 60 ? "text-yellow-500" : "text-green-500"}>
                  {memoryUsagePercent.toFixed(1)}%
                </span>
              </div>
              <Progress 
                value={memoryUsagePercent} 
                className={`h-2 ${
                  memoryUsagePercent > 80 ? "bg-red-100" : 
                  memoryUsagePercent > 60 ? "bg-yellow-100" : 
                  "bg-green-100"
                }`}
                indicatorClassName={
                  memoryUsagePercent > 80 ? "bg-red-500" : 
                  memoryUsagePercent > 60 ? "bg-yellow-500" : 
                  "bg-green-500"
                }
              />
              {metricsData?.memory?.heap && (
                <div className="flex justify-between text-xs text-muted-foreground">
                  <span>Used: {formatBytes(metricsData.memory.heap.used)}</span>
                  <span>Total: {formatBytes(metricsData.memory.heap.max)}</span>
                </div>
              )}
            </div>
            
            {/* CPU Usage */}
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Cpu className="h-5 w-5 text-blue-500" />
                  <span className="font-medium">CPU Usage</span>
                </div>
                <span className={cpuUsagePercent > 80 ? "text-red-500" : cpuUsagePercent > 60 ? "text-yellow-500" : "text-green-500"}>
                  {cpuUsagePercent.toFixed(1)}%
                </span>
              </div>
              <Progress 
                value={cpuUsagePercent} 
                className={`h-2 ${
                  cpuUsagePercent > 80 ? "bg-red-100" : 
                  cpuUsagePercent > 60 ? "bg-yellow-100" : 
                  "bg-green-100"
                }`}
                indicatorClassName={
                  cpuUsagePercent > 80 ? "bg-red-500" : 
                  cpuUsagePercent > 60 ? "bg-yellow-500" : 
                  "bg-green-500"
                }
              />
              {metricsData?.cpu && (
                <div className="flex justify-between text-xs text-muted-foreground">
                  <span>Cores: {metricsData.cpu.cores}</span>
                  <span>System Load: {metricsData.cpu.systemLoad.toFixed(2)}</span>
                </div>
              )}
            </div>
            
            {/* Component Status */}
            <div className="space-y-2">
              <h3 className="font-medium">Component Status</h3>
              <div className="space-y-1">
                {healthData?.checks?.map((check, index) => (
                  <div key={index} className="flex items-center justify-between py-1 border-b border-gray-100 dark:border-gray-800">
                    <span>{check.name}</span>
                    <div className="flex items-center gap-2">
                      <div className={`h-2 w-2 rounded-full ${getStatusColor(check.status)}`}></div>
                      <span>{check.status}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
            
            {/* Auto-refresh Toggle */}
            <div className="flex items-center justify-between pt-2">
              <span className="text-sm text-muted-foreground">Last updated: {new Date().toLocaleTimeString()}</span>
              <Button 
                variant="outline" 
                size="sm"
                onClick={() => setAutoRefresh(!autoRefresh)}
              >
                {autoRefresh ? "Disable Auto-refresh" : "Enable Auto-refresh"}
              </Button>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  )
}
