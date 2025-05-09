"use client"

import { useState, useEffect } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { 
  Area, 
  AreaChart, 
  Bar, 
  BarChart,
  CartesianGrid, 
  Legend,
  Line, 
  LineChart, 
  ResponsiveContainer, 
  Tooltip, 
  XAxis, 
  YAxis 
} from "recharts"
import { useTheme } from "next-themes"

interface TrafficData {
  name: string
  requests: number
  errors: number
  latency: number
}

interface EnhancedTrafficChartProps {
  data?: TrafficData[]
  isLoading?: boolean
  title?: string
  description?: string
}

export function EnhancedTrafficChart({
  data = [],
  isLoading = false,
  title = "Traffic Overview",
  description = "API traffic metrics over time"
}: EnhancedTrafficChartProps) {
  const { theme } = useTheme()
  const [chartType, setChartType] = useState<"line" | "area" | "bar">("area")
  const [timeRange, setTimeRange] = useState<"hour" | "day" | "week" | "month">("day")
  
  // Generate mock data if no data is provided
  const chartData = data.length > 0 ? data : generateMockData(timeRange)
  
  // Colors based on theme
  const colors = {
    requests: theme === "dark" ? "#3b82f6" : "#2563eb",
    errors: theme === "dark" ? "#ef4444" : "#dc2626",
    latency: theme === "dark" ? "#f59e0b" : "#d97706",
    grid: theme === "dark" ? "#333" : "#e5e7eb",
    text: theme === "dark" ? "#e5e7eb" : "#374151"
  }
  
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between pb-2">
        <div className="space-y-0.5">
          <CardTitle>{title}</CardTitle>
          <CardDescription>{description}</CardDescription>
        </div>
        <div className="flex items-center space-x-2">
          <Select
            value={timeRange}
            onValueChange={(value: "hour" | "day" | "week" | "month") => setTimeRange(value)}
          >
            <SelectTrigger className="w-[120px]">
              <SelectValue placeholder="Select range" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="hour">Last Hour</SelectItem>
              <SelectItem value="day">Last Day</SelectItem>
              <SelectItem value="week">Last Week</SelectItem>
              <SelectItem value="month">Last Month</SelectItem>
            </SelectContent>
          </Select>
          <Tabs defaultValue="area" value={chartType} onValueChange={(value: "line" | "area" | "bar") => setChartType(value)}>
            <TabsList className="grid w-[180px] grid-cols-3">
              <TabsTrigger value="line">Line</TabsTrigger>
              <TabsTrigger value="area">Area</TabsTrigger>
              <TabsTrigger value="bar">Bar</TabsTrigger>
            </TabsList>
          </Tabs>
        </div>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <div className="flex h-[350px] items-center justify-center">
            <div className="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent"></div>
          </div>
        ) : (
          <div className="h-[350px]">
            {chartType === "line" && (
              <ResponsiveContainer width="100%" height="100%">
                <LineChart
                  data={chartData}
                  margin={{ top: 5, right: 30, left: 20, bottom: 5 }}
                >
                  <CartesianGrid strokeDasharray="3 3" stroke={colors.grid} />
                  <XAxis 
                    dataKey="name" 
                    stroke={colors.text}
                    tick={{ fill: colors.text }}
                  />
                  <YAxis 
                    yAxisId="left"
                    stroke={colors.text}
                    tick={{ fill: colors.text }}
                  />
                  <YAxis 
                    yAxisId="right"
                    orientation="right"
                    stroke={colors.text}
                    tick={{ fill: colors.text }}
                  />
                  <Tooltip 
                    contentStyle={{ 
                      backgroundColor: theme === "dark" ? "#1f2937" : "#fff",
                      borderColor: colors.grid,
                      color: colors.text
                    }}
                  />
                  <Legend />
                  <Line
                    yAxisId="left"
                    type="monotone"
                    dataKey="requests"
                    name="Requests"
                    stroke={colors.requests}
                    activeDot={{ r: 8 }}
                  />
                  <Line
                    yAxisId="left"
                    type="monotone"
                    dataKey="errors"
                    name="Errors"
                    stroke={colors.errors}
                  />
                  <Line
                    yAxisId="right"
                    type="monotone"
                    dataKey="latency"
                    name="Latency (ms)"
                    stroke={colors.latency}
                  />
                </LineChart>
              </ResponsiveContainer>
            )}
            
            {chartType === "area" && (
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart
                  data={chartData}
                  margin={{ top: 5, right: 30, left: 20, bottom: 5 }}
                >
                  <CartesianGrid strokeDasharray="3 3" stroke={colors.grid} />
                  <XAxis 
                    dataKey="name" 
                    stroke={colors.text}
                    tick={{ fill: colors.text }}
                  />
                  <YAxis 
                    yAxisId="left"
                    stroke={colors.text}
                    tick={{ fill: colors.text }}
                  />
                  <YAxis 
                    yAxisId="right"
                    orientation="right"
                    stroke={colors.text}
                    tick={{ fill: colors.text }}
                  />
                  <Tooltip 
                    contentStyle={{ 
                      backgroundColor: theme === "dark" ? "#1f2937" : "#fff",
                      borderColor: colors.grid,
                      color: colors.text
                    }}
                  />
                  <Legend />
                  <Area
                    yAxisId="left"
                    type="monotone"
                    dataKey="requests"
                    name="Requests"
                    stroke={colors.requests}
                    fill={colors.requests}
                    fillOpacity={0.3}
                  />
                  <Area
                    yAxisId="left"
                    type="monotone"
                    dataKey="errors"
                    name="Errors"
                    stroke={colors.errors}
                    fill={colors.errors}
                    fillOpacity={0.3}
                  />
                  <Area
                    yAxisId="right"
                    type="monotone"
                    dataKey="latency"
                    name="Latency (ms)"
                    stroke={colors.latency}
                    fill={colors.latency}
                    fillOpacity={0.3}
                  />
                </AreaChart>
              </ResponsiveContainer>
            )}
            
            {chartType === "bar" && (
              <ResponsiveContainer width="100%" height="100%">
                <BarChart
                  data={chartData}
                  margin={{ top: 5, right: 30, left: 20, bottom: 5 }}
                >
                  <CartesianGrid strokeDasharray="3 3" stroke={colors.grid} />
                  <XAxis 
                    dataKey="name" 
                    stroke={colors.text}
                    tick={{ fill: colors.text }}
                  />
                  <YAxis 
                    yAxisId="left"
                    stroke={colors.text}
                    tick={{ fill: colors.text }}
                  />
                  <YAxis 
                    yAxisId="right"
                    orientation="right"
                    stroke={colors.text}
                    tick={{ fill: colors.text }}
                  />
                  <Tooltip 
                    contentStyle={{ 
                      backgroundColor: theme === "dark" ? "#1f2937" : "#fff",
                      borderColor: colors.grid,
                      color: colors.text
                    }}
                  />
                  <Legend />
                  <Bar
                    yAxisId="left"
                    dataKey="requests"
                    name="Requests"
                    fill={colors.requests}
                  />
                  <Bar
                    yAxisId="left"
                    dataKey="errors"
                    name="Errors"
                    fill={colors.errors}
                  />
                  <Bar
                    yAxisId="right"
                    dataKey="latency"
                    name="Latency (ms)"
                    fill={colors.latency}
                  />
                </BarChart>
              </ResponsiveContainer>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  )
}

// Helper function to generate mock data
function generateMockData(timeRange: "hour" | "day" | "week" | "month"): TrafficData[] {
  const data: TrafficData[] = []
  let points = 0
  let format = ""
  
  switch (timeRange) {
    case "hour":
      points = 12
      format = "HH:mm"
      break
    case "day":
      points = 24
      format = "HH:00"
      break
    case "week":
      points = 7
      format = "ddd"
      break
    case "month":
      points = 30
      format = "MMM D"
      break
  }
  
  for (let i = 0; i < points; i++) {
    const date = new Date()
    
    if (timeRange === "hour") {
      date.setMinutes(date.getMinutes() - (points - i) * 5)
      data.push({
        name: `${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`,
        requests: Math.floor(Math.random() * 1000) + 500,
        errors: Math.floor(Math.random() * 50),
        latency: Math.floor(Math.random() * 100) + 20
      })
    } else if (timeRange === "day") {
      date.setHours(date.getHours() - (points - i))
      data.push({
        name: `${date.getHours().toString().padStart(2, '0')}:00`,
        requests: Math.floor(Math.random() * 5000) + 1000,
        errors: Math.floor(Math.random() * 200),
        latency: Math.floor(Math.random() * 150) + 20
      })
    } else if (timeRange === "week") {
      date.setDate(date.getDate() - (points - i))
      data.push({
        name: date.toLocaleDateString('en-US', { weekday: 'short' }),
        requests: Math.floor(Math.random() * 50000) + 10000,
        errors: Math.floor(Math.random() * 1000),
        latency: Math.floor(Math.random() * 200) + 20
      })
    } else if (timeRange === "month") {
      date.setDate(date.getDate() - (points - i))
      data.push({
        name: date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' }),
        requests: Math.floor(Math.random() * 100000) + 50000,
        errors: Math.floor(Math.random() * 5000),
        latency: Math.floor(Math.random() * 250) + 20
      })
    }
  }
  
  return data
}
