"use client"

import { useState } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { 
  Cell,
  Legend,
  Pie, 
  PieChart, 
  ResponsiveContainer, 
  Tooltip,
  Treemap,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid
} from "recharts"
import { useTheme } from "next-themes"

interface LLMUsageData {
  name: string
  value: number
  tokens?: number
  cost?: number
}

interface EnhancedLLMUsageChartProps {
  data?: LLMUsageData[]
  isLoading?: boolean
  title?: string
  description?: string
}

export function EnhancedLLMUsageChart({
  data = [],
  isLoading = false,
  title = "LLM Usage",
  description = "Distribution of LLM usage by model"
}: EnhancedLLMUsageChartProps) {
  const { theme } = useTheme()
  const [chartType, setChartType] = useState<"pie" | "treemap" | "bar">("pie")
  const [metric, setMetric] = useState<"requests" | "tokens" | "cost">("requests")
  
  // Generate mock data if no data is provided
  const chartData = data.length > 0 ? data : generateMockData()
  
  // Colors for different models
  const COLORS = [
    "#3b82f6", "#ef4444", "#f59e0b", "#10b981", "#8b5cf6", 
    "#ec4899", "#06b6d4", "#84cc16", "#f97316", "#6366f1"
  ]
  
  // Format value based on metric
  const formatValue = (value: number) => {
    if (metric === "tokens") {
      return `${(value / 1000).toFixed(1)}k tokens`
    } else if (metric === "cost") {
      return `$${value.toFixed(2)}`
    }
    return value.toLocaleString()
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
            value={metric}
            onValueChange={(value: "requests" | "tokens" | "cost") => setMetric(value)}
          >
            <SelectTrigger className="w-[120px]">
              <SelectValue placeholder="Select metric" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="requests">Requests</SelectItem>
              <SelectItem value="tokens">Tokens</SelectItem>
              <SelectItem value="cost">Cost</SelectItem>
            </SelectContent>
          </Select>
          <Tabs defaultValue="pie" value={chartType} onValueChange={(value: "pie" | "treemap" | "bar") => setChartType(value)}>
            <TabsList className="grid w-[180px] grid-cols-3">
              <TabsTrigger value="pie">Pie</TabsTrigger>
              <TabsTrigger value="treemap">Treemap</TabsTrigger>
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
            {chartType === "pie" && (
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={chartData}
                    cx="50%"
                    cy="50%"
                    labelLine={false}
                    outerRadius={120}
                    fill="#8884d8"
                    dataKey={metric}
                    nameKey="name"
                    label={({ name, percent }) => `${name}: ${(percent * 100).toFixed(0)}%`}
                  >
                    {chartData.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                    ))}
                  </Pie>
                  <Tooltip
                    formatter={(value: number) => formatValue(value)}
                    contentStyle={{ 
                      backgroundColor: theme === "dark" ? "#1f2937" : "#fff",
                      borderColor: theme === "dark" ? "#333" : "#e5e7eb",
                      color: theme === "dark" ? "#e5e7eb" : "#374151"
                    }}
                  />
                  <Legend />
                </PieChart>
              </ResponsiveContainer>
            )}
            
            {chartType === "treemap" && (
              <ResponsiveContainer width="100%" height="100%">
                <Treemap
                  data={chartData}
                  dataKey={metric}
                  nameKey="name"
                  aspectRatio={4 / 3}
                  stroke="#fff"
                  fill="#8884d8"
                >
                  {chartData.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                  ))}
                  <Tooltip
                    formatter={(value: number) => formatValue(value)}
                    contentStyle={{ 
                      backgroundColor: theme === "dark" ? "#1f2937" : "#fff",
                      borderColor: theme === "dark" ? "#333" : "#e5e7eb",
                      color: theme === "dark" ? "#e5e7eb" : "#374151"
                    }}
                  />
                </Treemap>
              </ResponsiveContainer>
            )}
            
            {chartType === "bar" && (
              <ResponsiveContainer width="100%" height="100%">
                <BarChart
                  data={chartData}
                  layout="vertical"
                  margin={{ top: 5, right: 30, left: 100, bottom: 5 }}
                >
                  <CartesianGrid strokeDasharray="3 3" stroke={theme === "dark" ? "#333" : "#e5e7eb"} />
                  <XAxis 
                    type="number"
                    stroke={theme === "dark" ? "#e5e7eb" : "#374151"}
                    tick={{ fill: theme === "dark" ? "#e5e7eb" : "#374151" }}
                  />
                  <YAxis 
                    type="category"
                    dataKey="name" 
                    stroke={theme === "dark" ? "#e5e7eb" : "#374151"}
                    tick={{ fill: theme === "dark" ? "#e5e7eb" : "#374151" }}
                  />
                  <Tooltip 
                    formatter={(value: number) => formatValue(value)}
                    contentStyle={{ 
                      backgroundColor: theme === "dark" ? "#1f2937" : "#fff",
                      borderColor: theme === "dark" ? "#333" : "#e5e7eb",
                      color: theme === "dark" ? "#e5e7eb" : "#374151"
                    }}
                  />
                  <Bar dataKey={metric} name={metric.charAt(0).toUpperCase() + metric.slice(1)}>
                    {chartData.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                    ))}
                  </Bar>
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
function generateMockData(): LLMUsageData[] {
  return [
    { 
      name: "GPT-4", 
      requests: 12500, 
      tokens: 2500000, 
      cost: 75.00 
    },
    { 
      name: "GPT-3.5 Turbo", 
      requests: 45000, 
      tokens: 9000000, 
      cost: 18.00 
    },
    { 
      name: "Claude 3 Opus", 
      requests: 8000, 
      tokens: 1600000, 
      cost: 48.00 
    },
    { 
      name: "Claude 3 Sonnet", 
      requests: 15000, 
      tokens: 3000000, 
      cost: 30.00 
    },
    { 
      name: "Llama 3", 
      requests: 20000, 
      tokens: 4000000, 
      cost: 12.00 
    }
  ]
}
