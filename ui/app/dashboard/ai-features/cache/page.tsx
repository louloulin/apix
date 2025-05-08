"use client"

import { useState, useEffect } from "react"
import { DashboardLayout } from "@/components/layout/dashboard-layout"
import { Card, CardContent, CardDescription, CardHeader, CardTitle, CardFooter } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { AlertCircle, RefreshCw } from "lucide-react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Progress } from "@/components/ui/progress"
import { useToast } from "@/components/ui/use-toast"

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
  const [cacheStats, setCacheStats] = useState<CacheStats | null>(null)
  const [loading, setLoading] = useState(true)
  const [clearing, setClearing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [selectedModel, setSelectedModel] = useState<string>("all")
  const { toast } = useToast()

  const fetchCacheStats = async () => {
    try {
      setLoading(true)
      const response = await fetch('/api/ai/cache/stats')
      if (!response.ok) {
        throw new Error('Failed to fetch cache statistics')
      }
      const data = await response.json()
      setCacheStats(data.stats || null)
    } catch (err) {
      setError('Error loading cache statistics: ' + (err instanceof Error ? err.message : String(err)))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchCacheStats()
  }, [])

  const clearCache = async () => {
    try {
      setClearing(true)
      const url = selectedModel === "all" 
        ? '/api/ai/cache/clear' 
        : `/api/ai/cache/clear?modelId=${selectedModel}`
      
      const response = await fetch(url, { method: 'POST' })
      if (!response.ok) {
        throw new Error('Failed to clear cache')
      }
      
      const data = await response.json()
      
      toast({
        title: "Cache cleared",
        description: data.message || `Cleared ${data.count || 'all'} cache entries`,
      })
      
      // Refresh stats
      fetchCacheStats()
    } catch (err) {
      setError('Error clearing cache: ' + (err instanceof Error ? err.message : String(err)))
      toast({
        variant: "destructive",
        title: "Error",
        description: 'Failed to clear cache: ' + (err instanceof Error ? err.message : String(err)),
      })
    } finally {
      setClearing(false)
    }
  }

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
          <Button onClick={fetchCacheStats} variant="outline" disabled={loading}>
            <RefreshCw className={`h-4 w-4 mr-2 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
        </div>

        {error && (
          <Alert variant="destructive">
            <AlertCircle className="h-4 w-4" />
            <AlertTitle>Error</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        <div className="flex flex-col gap-4 md:flex-row">
          <Card className="flex-1">
            <CardHeader>
              <CardTitle>Cache Management</CardTitle>
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
                disabled={clearing || loading}
                variant="destructive"
                className="w-full"
              >
                {clearing ? 'Clearing...' : `Clear ${selectedModel === 'all' ? 'All' : selectedModel} Cache`}
              </Button>
            </CardFooter>
          </Card>
        </div>

        {loading ? (
          <p>Loading cache statistics...</p>
        ) : cacheStats ? (
          <>
            <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
              <Card>
                <CardHeader className="pb-2">
                  <CardTitle className="text-sm font-medium">Cache Hit Rate</CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">{formatPercentage(cacheStats.total.hitRate)}</div>
                  <Progress 
                    value={cacheStats.total.hitRate * 100} 
                    className="mt-2"
                  />
                </CardContent>
              </Card>
              
              <Card>
                <CardHeader className="pb-2">
                  <CardTitle className="text-sm font-medium">Cache Hits</CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">{formatNumber(cacheStats.total.hits)}</div>
                </CardContent>
              </Card>
              
              <Card>
                <CardHeader className="pb-2">
                  <CardTitle className="text-sm font-medium">Cache Misses</CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">{formatNumber(cacheStats.total.misses)}</div>
                </CardContent>
              </Card>
              
              <Card>
                <CardHeader className="pb-2">
                  <CardTitle className="text-sm font-medium">Cache Size</CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">
                    {cacheStats.total.size >= 0 ? formatNumber(cacheStats.total.size) : 'N/A'}
                  </div>
                  <p className="text-xs text-muted-foreground">
                    {cacheStats.total.size < 0 ? 'Size not available in cluster mode' : 'Entries'}
                  </p>
                </CardContent>
              </Card>
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
