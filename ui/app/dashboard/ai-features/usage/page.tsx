"use client"

import { useState, useEffect } from "react"
import { DashboardLayout } from "@/components/layout/dashboard-layout"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { AlertCircle } from "lucide-react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { DateRangePicker } from "@/components/ui/date-range-picker"

interface UsageStats {
  total_prompt_tokens: number
  total_completion_tokens: number
  total_tokens: number
  total_requests: number
  daily: Record<string, DailyStats>
}

interface DailyStats {
  prompt_tokens: number
  completion_tokens: number
  total_tokens: number
  requests: number
  models?: Record<string, ModelStats>
  users?: Record<string, UserStats>
}

interface ModelStats {
  prompt_tokens: number
  completion_tokens: number
  total_tokens: number
  requests: number
}

interface UserStats {
  prompt_tokens: number
  completion_tokens: number
  total_tokens: number
  requests: number
}

export default function UsagePage() {
  const [usageStats, setUsageStats] = useState<UsageStats | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selectedModel, setSelectedModel] = useState<string>("all")
  const [dateRange, setDateRange] = useState<{ from: Date; to: Date }>({
    from: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000), // 30 days ago
    to: new Date()
  })

  useEffect(() => {
    const fetchUsageStats = async () => {
      try {
        const response = await fetch('/api/ai/usage')
        if (!response.ok) {
          throw new Error('Failed to fetch usage statistics')
        }
        const data = await response.json()
        setUsageStats(data.usage || null)
      } catch (err) {
        setError('Error loading usage statistics: ' + (err instanceof Error ? err.message : String(err)))
      } finally {
        setLoading(false)
      }
    }

    fetchUsageStats()
  }, [])

  // Format large numbers with commas
  const formatNumber = (num: number) => {
    return num.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",")
  }

  // Get available models from usage stats
  const getAvailableModels = () => {
    if (!usageStats || !usageStats.daily) return []
    
    const models = new Set<string>()
    
    Object.values(usageStats.daily).forEach(day => {
      if (day.models) {
        Object.keys(day.models).forEach(model => models.add(model))
      }
    })
    
    return Array.from(models)
  }

  // Filter usage stats by date range and model
  const getFilteredStats = () => {
    if (!usageStats) return null
    
    const filtered: UsageStats = {
      total_prompt_tokens: 0,
      total_completion_tokens: 0,
      total_tokens: 0,
      total_requests: 0,
      daily: {}
    }
    
    Object.entries(usageStats.daily).forEach(([date, stats]) => {
      const dateObj = new Date(date)
      if (dateObj >= dateRange.from && dateObj <= dateRange.to) {
        if (selectedModel === "all") {
          filtered.daily[date] = stats
          filtered.total_prompt_tokens += stats.prompt_tokens
          filtered.total_completion_tokens += stats.completion_tokens
          filtered.total_tokens += stats.total_tokens
          filtered.total_requests += stats.requests
        } else if (stats.models && stats.models[selectedModel]) {
          const modelStats = stats.models[selectedModel]
          filtered.daily[date] = {
            prompt_tokens: modelStats.prompt_tokens,
            completion_tokens: modelStats.completion_tokens,
            total_tokens: modelStats.total_tokens,
            requests: modelStats.requests
          }
          filtered.total_prompt_tokens += modelStats.prompt_tokens
          filtered.total_completion_tokens += modelStats.completion_tokens
          filtered.total_tokens += modelStats.total_tokens
          filtered.total_requests += modelStats.requests
        }
      }
    })
    
    return filtered
  }

  const filteredStats = getFilteredStats()
  const availableModels = getAvailableModels()

  return (
    <DashboardLayout>
      <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold">AI Usage Statistics</h1>
            <p className="text-muted-foreground">
              Monitor token usage and request statistics
            </p>
          </div>
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
              <CardTitle>Filters</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div>
                <label className="text-sm font-medium">Date Range</label>
                <DateRangePicker 
                  value={dateRange}
                  onChange={setDateRange}
                />
              </div>
              <div>
                <label className="text-sm font-medium">Model</label>
                <Select value={selectedModel} onValueChange={setSelectedModel}>
                  <SelectTrigger>
                    <SelectValue placeholder="Select model" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">All Models</SelectItem>
                    {availableModels.map(model => (
                      <SelectItem key={model} value={model}>{model}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </CardContent>
          </Card>
        </div>

        {loading ? (
          <p>Loading usage statistics...</p>
        ) : filteredStats ? (
          <>
            <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
              <Card>
                <CardHeader className="pb-2">
                  <CardTitle className="text-sm font-medium">Total Tokens</CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">{formatNumber(filteredStats.total_tokens)}</div>
                  <p className="text-xs text-muted-foreground">
                    Prompt: {formatNumber(filteredStats.total_prompt_tokens)} | 
                    Completion: {formatNumber(filteredStats.total_completion_tokens)}
                  </p>
                </CardContent>
              </Card>
              
              <Card>
                <CardHeader className="pb-2">
                  <CardTitle className="text-sm font-medium">Total Requests</CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">{formatNumber(filteredStats.total_requests)}</div>
                  <p className="text-xs text-muted-foreground">
                    Avg. Tokens per Request: {filteredStats.total_requests > 0 
                      ? Math.round(filteredStats.total_tokens / filteredStats.total_requests) 
                      : 0}
                  </p>
                </CardContent>
              </Card>
              
              <Card>
                <CardHeader className="pb-2">
                  <CardTitle className="text-sm font-medium">Prompt Tokens</CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">{formatNumber(filteredStats.total_prompt_tokens)}</div>
                  <p className="text-xs text-muted-foreground">
                    {filteredStats.total_tokens > 0 
                      ? Math.round((filteredStats.total_prompt_tokens / filteredStats.total_tokens) * 100) 
                      : 0}% of total tokens
                  </p>
                </CardContent>
              </Card>
              
              <Card>
                <CardHeader className="pb-2">
                  <CardTitle className="text-sm font-medium">Completion Tokens</CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">{formatNumber(filteredStats.total_completion_tokens)}</div>
                  <p className="text-xs text-muted-foreground">
                    {filteredStats.total_tokens > 0 
                      ? Math.round((filteredStats.total_completion_tokens / filteredStats.total_tokens) * 100) 
                      : 0}% of total tokens
                  </p>
                </CardContent>
              </Card>
            </div>

            <Tabs defaultValue="daily">
              <TabsList>
                <TabsTrigger value="daily">Daily Usage</TabsTrigger>
                <TabsTrigger value="models">By Model</TabsTrigger>
                {usageStats?.daily && Object.values(usageStats.daily).some(day => day.users) && (
                  <TabsTrigger value="users">By User</TabsTrigger>
                )}
              </TabsList>
              
              <TabsContent value="daily">
                <Card>
                  <CardHeader>
                    <CardTitle>Daily Usage</CardTitle>
                    <CardDescription>Token usage by day</CardDescription>
                  </CardHeader>
                  <CardContent>
                    <div className="space-y-8">
                      <div className="overflow-x-auto">
                        <table className="w-full">
                          <thead>
                            <tr className="border-b">
                              <th className="text-left p-2">Date</th>
                              <th className="text-right p-2">Prompt Tokens</th>
                              <th className="text-right p-2">Completion Tokens</th>
                              <th className="text-right p-2">Total Tokens</th>
                              <th className="text-right p-2">Requests</th>
                            </tr>
                          </thead>
                          <tbody>
                            {Object.entries(filteredStats.daily)
                              .sort((a, b) => new Date(b[0]).getTime() - new Date(a[0]).getTime())
                              .map(([date, stats]) => (
                                <tr key={date} className="border-b">
                                  <td className="p-2">{new Date(date).toLocaleDateString()}</td>
                                  <td className="text-right p-2">{formatNumber(stats.prompt_tokens)}</td>
                                  <td className="text-right p-2">{formatNumber(stats.completion_tokens)}</td>
                                  <td className="text-right p-2">{formatNumber(stats.total_tokens)}</td>
                                  <td className="text-right p-2">{formatNumber(stats.requests)}</td>
                                </tr>
                              ))}
                          </tbody>
                        </table>
                      </div>
                    </div>
                  </CardContent>
                </Card>
              </TabsContent>
              
              <TabsContent value="models">
                <Card>
                  <CardHeader>
                    <CardTitle>Usage by Model</CardTitle>
                    <CardDescription>Token usage by AI model</CardDescription>
                  </CardHeader>
                  <CardContent>
                    <div className="space-y-8">
                      <div className="overflow-x-auto">
                        <table className="w-full">
                          <thead>
                            <tr className="border-b">
                              <th className="text-left p-2">Model</th>
                              <th className="text-right p-2">Prompt Tokens</th>
                              <th className="text-right p-2">Completion Tokens</th>
                              <th className="text-right p-2">Total Tokens</th>
                              <th className="text-right p-2">Requests</th>
                            </tr>
                          </thead>
                          <tbody>
                            {(() => {
                              // Aggregate model stats across all days
                              const modelStats: Record<string, ModelStats> = {}
                              
                              Object.values(filteredStats.daily).forEach(day => {
                                if (day.models) {
                                  Object.entries(day.models).forEach(([model, stats]) => {
                                    if (!modelStats[model]) {
                                      modelStats[model] = {
                                        prompt_tokens: 0,
                                        completion_tokens: 0,
                                        total_tokens: 0,
                                        requests: 0
                                      }
                                    }
                                    
                                    modelStats[model].prompt_tokens += stats.prompt_tokens
                                    modelStats[model].completion_tokens += stats.completion_tokens
                                    modelStats[model].total_tokens += stats.total_tokens
                                    modelStats[model].requests += stats.requests
                                  })
                                }
                              })
                              
                              return Object.entries(modelStats)
                                .sort((a, b) => b[1].total_tokens - a[1].total_tokens)
                                .map(([model, stats]) => (
                                  <tr key={model} className="border-b">
                                    <td className="p-2">{model}</td>
                                    <td className="text-right p-2">{formatNumber(stats.prompt_tokens)}</td>
                                    <td className="text-right p-2">{formatNumber(stats.completion_tokens)}</td>
                                    <td className="text-right p-2">{formatNumber(stats.total_tokens)}</td>
                                    <td className="text-right p-2">{formatNumber(stats.requests)}</td>
                                  </tr>
                                ))
                            })()}
                          </tbody>
                        </table>
                      </div>
                    </div>
                  </CardContent>
                </Card>
              </TabsContent>
              
              {usageStats?.daily && Object.values(usageStats.daily).some(day => day.users) && (
                <TabsContent value="users">
                  <Card>
                    <CardHeader>
                      <CardTitle>Usage by User</CardTitle>
                      <CardDescription>Token usage by user</CardDescription>
                    </CardHeader>
                    <CardContent>
                      <div className="space-y-8">
                        <div className="overflow-x-auto">
                          <table className="w-full">
                            <thead>
                              <tr className="border-b">
                                <th className="text-left p-2">User</th>
                                <th className="text-right p-2">Prompt Tokens</th>
                                <th className="text-right p-2">Completion Tokens</th>
                                <th className="text-right p-2">Total Tokens</th>
                                <th className="text-right p-2">Requests</th>
                              </tr>
                            </thead>
                            <tbody>
                              {(() => {
                                // Aggregate user stats across all days
                                const userStats: Record<string, UserStats> = {}
                                
                                Object.values(filteredStats.daily).forEach(day => {
                                  if (day.users) {
                                    Object.entries(day.users).forEach(([user, stats]) => {
                                      if (!userStats[user]) {
                                        userStats[user] = {
                                          prompt_tokens: 0,
                                          completion_tokens: 0,
                                          total_tokens: 0,
                                          requests: 0
                                        }
                                      }
                                      
                                      userStats[user].prompt_tokens += stats.prompt_tokens
                                      userStats[user].completion_tokens += stats.completion_tokens
                                      userStats[user].total_tokens += stats.total_tokens
                                      userStats[user].requests += stats.requests
                                    })
                                  }
                                })
                                
                                return Object.entries(userStats)
                                  .sort((a, b) => b[1].total_tokens - a[1].total_tokens)
                                  .map(([user, stats]) => (
                                    <tr key={user} className="border-b">
                                      <td className="p-2">{user}</td>
                                      <td className="text-right p-2">{formatNumber(stats.prompt_tokens)}</td>
                                      <td className="text-right p-2">{formatNumber(stats.completion_tokens)}</td>
                                      <td className="text-right p-2">{formatNumber(stats.total_tokens)}</td>
                                      <td className="text-right p-2">{formatNumber(stats.requests)}</td>
                                    </tr>
                                  ))
                              })()}
                            </tbody>
                          </table>
                        </div>
                      </div>
                    </CardContent>
                  </Card>
                </TabsContent>
              )}
            </Tabs>
          </>
        ) : (
          <Card>
            <CardContent className="py-10">
              <div className="flex flex-col items-center justify-center">
                <p className="text-muted-foreground">No usage data available</p>
              </div>
            </CardContent>
          </Card>
        )}
      </div>
    </DashboardLayout>
  )
}
