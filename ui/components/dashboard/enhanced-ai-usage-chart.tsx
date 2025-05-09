"use client"

import { useState, useEffect } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { useTranslations } from 'next-intl'
import {
  AreaChart,
  Area,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell
} from "recharts"

// Types
interface AIUsageData {
  date: string
  requests: number
  tokens: number
  cost: number
  models: {
    [key: string]: {
      requests: number
      tokens: number
      cost: number
    }
  }
}

interface ModelUsage {
  name: string
  requests: number
  tokens: number
  cost: number
  color: string
}

interface EnhancedAIUsageChartProps {
  data?: AIUsageData[]
  isLoading?: boolean
  error?: Error | null
  className?: string
}

// Colors for different models
const MODEL_COLORS = [
  "#8884d8",
  "#82ca9d",
  "#ffc658",
  "#ff8042",
  "#0088fe",
  "#00c49f",
  "#ffbb28",
  "#ff8042"
]

export function EnhancedAIUsageChart({
  data = [],
  isLoading = false,
  error = null,
  className
}: EnhancedAIUsageChartProps) {
  const t = useTranslations('dashboard')
  const [activeTab, setActiveTab] = useState("requests")
  const [timeRange, setTimeRange] = useState("7d")
  const [chartType, setChartType] = useState("area")

  // Filter data based on time range
  const filteredData = data.slice(-getTimeRangeDays(timeRange))

  // Get model usage data for pie chart
  const modelUsageData = getModelUsageData(filteredData)

  // Format data for the selected metric
  const formattedData = formatDataForMetric(filteredData, activeTab)

  // Helper function to get number of days for time range
  function getTimeRangeDays(range: string): number {
    switch (range) {
      case "24h": return 1
      case "7d": return 7
      case "30d": return 30
      case "90d": return 90
      default: return 7
    }
  }

  // Helper function to get model usage data
  function getModelUsageData(data: AIUsageData[]): ModelUsage[] {
    const modelMap: Record<string, ModelUsage> = {}
    
    data.forEach(day => {
      Object.entries(day.models || {}).forEach(([modelName, usage], index) => {
        if (!modelMap[modelName]) {
          modelMap[modelName] = {
            name: modelName,
            requests: 0,
            tokens: 0,
            cost: 0,
            color: MODEL_COLORS[Object.keys(modelMap).length % MODEL_COLORS.length]
          }
        }
        
        modelMap[modelName].requests += usage.requests
        modelMap[modelName].tokens += usage.tokens
        modelMap[modelName].cost += usage.cost
      })
    })
    
    return Object.values(modelMap).sort((a, b) => b[activeTab as keyof ModelUsage] - a[activeTab as keyof ModelUsage])
  }

  // Helper function to format data for the selected metric
  function formatDataForMetric(data: AIUsageData[], metric: string) {
    return data.map(day => {
      const formattedDay: any = { date: day.date }
      
      // Add total for the metric
      formattedDay[metric] = day[metric as keyof AIUsageData] as number
      
      // Add per-model data
      Object.entries(day.models || {}).forEach(([modelName, usage]) => {
        formattedDay[modelName] = usage[metric as keyof typeof usage] as number
      })
      
      return formattedDay
    })
  }

  // Helper function to format values
  function formatValue(value: number, metric: string): string {
    switch (metric) {
      case "requests":
        return value.toLocaleString()
      case "tokens":
        return value >= 1000000
          ? `${(value / 1000000).toFixed(2)}M`
          : value >= 1000
          ? `${(value / 1000).toFixed(1)}K`
          : value.toString()
      case "cost":
        return `$${value.toFixed(2)}`
      default:
        return value.toString()
    }
  }

  // Get y-axis label based on metric
  function getYAxisLabel(metric: string): string {
    switch (metric) {
      case "requests": return "Requests"
      case "tokens": return "Tokens"
      case "cost": return "Cost ($)"
      default: return ""
    }
  }

  // Render loading state
  if (isLoading) {
    return (
      <Card className={className}>
        <CardHeader>
          <CardTitle>{t('aiUsage')}</CardTitle>
          <CardDescription>
            {t('loading')}
          </CardDescription>
        </CardHeader>
        <CardContent className="h-80 flex items-center justify-center">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
        </CardContent>
      </Card>
    )
  }

  // Render error state
  if (error) {
    return (
      <Card className={className}>
        <CardHeader>
          <CardTitle>{t('aiUsage')}</CardTitle>
          <CardDescription className="text-red-500">
            {error.message}
          </CardDescription>
        </CardHeader>
        <CardContent className="h-80 flex items-center justify-center text-muted-foreground">
          Failed to load AI usage data
        </CardContent>
      </Card>
    )
  }

  // Render empty state
  if (data.length === 0) {
    return (
      <Card className={className}>
        <CardHeader>
          <CardTitle>{t('aiUsage')}</CardTitle>
          <CardDescription>
            AI usage metrics for your gateway
          </CardDescription>
        </CardHeader>
        <CardContent className="h-80 flex items-center justify-center text-muted-foreground">
          No AI usage data available
        </CardContent>
      </Card>
    )
  }

  return (
    <Card className={className}>
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
        <div>
          <CardTitle>{t('aiUsage')}</CardTitle>
          <CardDescription>
            AI usage metrics for your gateway
          </CardDescription>
        </div>
        <div className="flex items-center gap-2">
          <Select
            value={timeRange}
            onValueChange={setTimeRange}
          >
            <SelectTrigger className="w-[100px]">
              <SelectValue placeholder="Time Range" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="24h">24 Hours</SelectItem>
              <SelectItem value="7d">7 Days</SelectItem>
              <SelectItem value="30d">30 Days</SelectItem>
              <SelectItem value="90d">90 Days</SelectItem>
            </SelectContent>
          </Select>
          <Select
            value={chartType}
            onValueChange={setChartType}
          >
            <SelectTrigger className="w-[100px]">
              <SelectValue placeholder="Chart Type" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="area">Area</SelectItem>
              <SelectItem value="bar">Bar</SelectItem>
              <SelectItem value="pie">Pie</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </CardHeader>
      <CardContent>
        <Tabs value={activeTab} onValueChange={setActiveTab}>
          <TabsList className="mb-4">
            <TabsTrigger value="requests">Requests</TabsTrigger>
            <TabsTrigger value="tokens">Tokens</TabsTrigger>
            <TabsTrigger value="cost">Cost</TabsTrigger>
          </TabsList>
          
          <div className="h-80">
            {chartType === "area" && (
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart
                  data={formattedData}
                  margin={{ top: 10, right: 30, left: 0, bottom: 0 }}
                >
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="date" />
                  <YAxis label={{ value: getYAxisLabel(activeTab), angle: -90, position: 'insideLeft' }} />
                  <Tooltip 
                    formatter={(value: number) => formatValue(value, activeTab)}
                  />
                  <Legend />
                  <Area
                    type="monotone"
                    dataKey={activeTab}
                    name="Total"
                    stroke="#8884d8"
                    fill="#8884d8"
                    fillOpacity={0.3}
                  />
                  {modelUsageData.slice(0, 5).map((model, index) => (
                    <Area
                      key={model.name}
                      type="monotone"
                      dataKey={model.name}
                      name={model.name}
                      stroke={model.color}
                      fill={model.color}
                      fillOpacity={0.3}
                    />
                  ))}
                </AreaChart>
              </ResponsiveContainer>
            )}
            
            {chartType === "bar" && (
              <ResponsiveContainer width="100%" height="100%">
                <BarChart
                  data={formattedData}
                  margin={{ top: 10, right: 30, left: 0, bottom: 0 }}
                >
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="date" />
                  <YAxis label={{ value: getYAxisLabel(activeTab), angle: -90, position: 'insideLeft' }} />
                  <Tooltip 
                    formatter={(value: number) => formatValue(value, activeTab)}
                  />
                  <Legend />
                  {modelUsageData.slice(0, 5).map((model, index) => (
                    <Bar
                      key={model.name}
                      dataKey={model.name}
                      name={model.name}
                      fill={model.color}
                    />
                  ))}
                </BarChart>
              </ResponsiveContainer>
            )}
            
            {chartType === "pie" && (
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={modelUsageData}
                    cx="50%"
                    cy="50%"
                    labelLine={true}
                    outerRadius={120}
                    fill="#8884d8"
                    dataKey={activeTab}
                    nameKey="name"
                    label={({ name, percent }) => `${name}: ${(percent * 100).toFixed(0)}%`}
                  >
                    {modelUsageData.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={entry.color} />
                    ))}
                  </Pie>
                  <Tooltip 
                    formatter={(value: number) => formatValue(value, activeTab)}
                  />
                  <Legend />
                </PieChart>
              </ResponsiveContainer>
            )}
          </div>
        </Tabs>
      </CardContent>
    </Card>
  )
}
