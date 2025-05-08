"use client"

import { useState } from "react"
import { DashboardLayout } from "@/components/layout/dashboard-layout"
import { Card, CardContent, CardDescription, CardHeader, CardTitle, CardFooter } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { AlertCircle, Clock, Database, RefreshCw, Trash2 } from "lucide-react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Progress } from "@/components/ui/progress"
import { useToast } from "@/components/ui/use-toast"
import { DataCard } from "@/components/ui/data-card"
import { useApiData } from "@/lib/hooks/use-api-data"
import { useApiMutation } from "@/lib/hooks/use-api-data"
import { aiApi } from "@/lib/api-client"

interface CacheStats {
  total: {
    modelId: string
    hits: number
    misses: number
    hitRate: number
    size: number
  }
  models: Array<{
    modelId: string
    hits: number
    misses: number
    hitRate: number
  }>
}

export default function CachePage() {
  const [selectedModel, setSelectedModel] = useState<string>("all")
  const { toast } = useToast()

  const {
    data: cacheData,
    isLoading,
    error,
    refetch,
    isRefetching
  } = useApiData(
    () => aiApi.getCacheStats(),
    {
      onError: (err) => {
        toast({
          variant: "destructive",
          title: "Error loading cache statistics",
          description: err.message,
        })
      }
    }
  )

  const {
    mutate: clearCacheMutate,
    isLoading: isClearing
  } = useApiMutation(
    (modelId?: string) => aiApi.clearCache(modelId),
    {
      onSuccess: (data) => {
        toast({
          title: "Cache cleared",
          description: data.message || `Cleared ${data.count || 'all'} cache entries`,
        })
        refetch()
      },
      onError: (err) => {
        toast({
          variant: "destructive",
          title: "Error",
          description: 'Failed to clear cache: ' + err.message,
        })
      }
    }
  )

  const clearCache = () => {
    clearCacheMutate(selectedModel === "all" ? undefined : selectedModel)
  }

  const cacheStats = cacheData?.stats || null

  // Format large numbers with commas
  const formatNumber = (num: number) => {
    return num.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",")
  }

  // Format percentage
  const formatPercentage = (value: number) => {
    return `${(value * 100).toFixed(2)}%`
  }

  return (
    <DashboardLayout>
      <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold">AI Response Cache</h1>
            <p className="text-muted-foreground">
              Manage AI response caching to improve performance and reduce costs
            </p>
          </div>
          <Button
            onClick={() => refetch()}
            variant="outline"
            size="icon"
            disabled={isRefetching}
          >
            <RefreshCw className={`h-4 w-4 ${isRefetching ? 'animate-spin' : ''}`} />
          </Button>
        </div>

        {error && (
          <Alert variant="destructive">
            <AlertCircle className="h-4 w-4" />
            <AlertTitle>Error</AlertTitle>
            <AlertDescription>{error.message}</AlertDescription>
          </Alert>
        )}

        <div className="flex flex-col gap-4 md:flex-row">
          <Card className="flex-1">
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle>Cache Management</CardTitle>
                <Database className="h-4 w-4 text-muted-foreground" />
              </div>
              <CardDescription>Clear cache entries to free up memory</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div>
                <label className="text-sm font-medium">Model</label>
                <Select value={selectedModel} onValueChange={setSelectedModel}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select model" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">All Models</SelectItem>
                    {cacheStats?.models.map(model => (
                      <SelectItem key={model.modelId} value={model.modelId}>{model.modelId}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </CardContent>
            <CardFooter>
              <Button
                onClick={clearCache}
                disabled={isClearing || isLoading}
                variant="destructive"
                className="w-full flex items-center gap-2"
              >
                <Trash2 className="h-4 w-4" />
                {isClearing ? 'Clearing...' : `Clear ${selectedModel === 'all' ? 'All' : selectedModel} Cache`}
              </Button>
            </CardFooter>
          </Card>
        </div>

        {isLoading ? (
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
            {Array(4).fill(0).map((_, i) => (
              <Card key={i} className="opacity-70">
                <CardHeader className="pb-2">
                  <div className="h-5 w-24 bg-muted rounded animate-pulse"></div>
                </CardHeader>
                <CardContent>
                  <div className="h-8 w-32 bg-muted rounded animate-pulse mb-2"></div>
                  <div className="h-4 w-full bg-muted rounded animate-pulse"></div>
                </CardContent>
              </Card>
            ))}
          </div>
        ) : cacheStats ? (
          <>
            <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
              <DataCard
                title="Cache Hit Rate"
                value={formatPercentage(cacheStats.total.hitRate)}
                icon={<Clock className="h-4 w-4" />}
                description={
                  <Progress
                    value={cacheStats.total.hitRate * 100}
                    className="mt-2"
                  />
                }
              />

              <DataCard
                title="Cache Hits"
                value={formatNumber(cacheStats.total.hits)}
                icon={<Clock className="h-4 w-4" />}
              />

              <DataCard
                title="Cache Misses"
                value={formatNumber(cacheStats.total.misses)}
                icon={<Clock className="h-4 w-4" />}
              />

              <DataCard
                title="Cache Size"
                value={cacheStats.total.size >= 0 ? formatNumber(cacheStats.total.size) : 'N/A'}
                description={cacheStats.total.size < 0 ? 'Size not available in cluster mode' : 'Entries'}
                icon={<Database className="h-4 w-4" />}
              />
            </div>

            <Card>
              <CardHeader>
                <CardTitle>Cache Statistics by Model</CardTitle>
                <CardDescription>Hit rates and usage by AI model</CardDescription>
              </CardHeader>
              <CardContent>
                <div className="space-y-8">
                  <div className="overflow-x-auto">
                    <table className="w-full">
                      <thead>
                        <tr className="border-b">
                          <th className="text-left p-2">Model</th>
                          <th className="text-right p-2">Hits</th>
                          <th className="text-right p-2">Misses</th>
                          <th className="text-right p-2">Total Requests</th>
                          <th className="text-right p-2">Hit Rate</th>
                          <th className="text-right p-2">Actions</th>
                        </tr>
                      </thead>
                      <tbody>
                        {cacheStats.models
                          .sort((a, b) => b.hits + b.misses - (a.hits + a.misses))
                          .map(model => (
                            <tr key={model.modelId} className="border-b">
                              <td className="p-2">{model.modelId}</td>
                              <td className="text-right p-2">{formatNumber(model.hits)}</td>
                              <td className="text-right p-2">{formatNumber(model.misses)}</td>
                              <td className="text-right p-2">{formatNumber(model.hits + model.misses)}</td>
                              <td className="text-right p-2">{formatPercentage(model.hitRate)}</td>
                              <td className="text-right p-2">
                                <Button
                                  variant="outline"
                                  size="sm"
                                  onClick={() => {
                                    setSelectedModel(model.modelId)
                                    clearCache()
                                  }}
                                  disabled={clearing}
                                >
                                  Clear
                                </Button>
                              </td>
                            </tr>
                          ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              </CardContent>
            </Card>
          </>
        ) : (
          <Card>
            <CardContent className="py-10">
              <div className="flex flex-col items-center justify-center">
                <p className="text-muted-foreground">No cache statistics available</p>
              </div>
            </CardContent>
          </Card>
        )}
      </div>
    </DashboardLayout>
  )
}
