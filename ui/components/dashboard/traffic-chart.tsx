"use client";

import { useState, useMemo } from "react";
import { useTranslations } from "next-intl";
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  Area,
  AreaChart
} from "recharts";
import { TrafficDataPoint } from "@/lib/api-client/dashboard";

// 当没有数据时显示的空数据
const emptyData = [
  {
    date: new Date().toLocaleDateString("en-US", { month: 'short', day: 'numeric' }),
    requests: 0,
    successRate: 0,
    avgResponseTime: 0
  }
]

interface TrafficChartProps {
  data: TrafficDataPoint[]
}

export function TrafficChart({ data: externalData }: TrafficChartProps) {
  const t = useTranslations('dashboard')
  const [activeView, setActiveView] = useState("requests")

  // 使用外部数据或空数据
  const data = useMemo(() => {
    return externalData && externalData.length > 0 ? externalData : emptyData
  }, [externalData])

  return (
    <div className="h-full w-full">
      <div className="mb-4 flex items-center space-x-2">
        <button
          onClick={() => setActiveView("requests")}
          className={`px-3 py-1 text-sm rounded-md ${activeView === "requests"
            ? "bg-primary text-primary-foreground"
            : "bg-secondary text-secondary-foreground"}`}
        >
          {t('requests')}
        </button>
        <button
          onClick={() => setActiveView("responseTimes")}
          className={`px-3 py-1 text-sm rounded-md ${activeView === "responseTimes"
            ? "bg-primary text-primary-foreground"
            : "bg-secondary text-secondary-foreground"}`}
        >
          {t('avgResponseTime')}
        </button>
        <button
          onClick={() => setActiveView("successRate")}
          className={`px-3 py-1 text-sm rounded-md ${activeView === "successRate"
            ? "bg-primary text-primary-foreground"
            : "bg-secondary text-secondary-foreground"}`}
        >
          {t('successRate')}
        </button>
      </div>

      <ResponsiveContainer width="100%" height={250}>
        {activeView === "requests" ? (
          <AreaChart data={data}>
            <defs>
              <linearGradient id="colorRequests" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#8884d8" stopOpacity={0.8}/>
                <stop offset="95%" stopColor="#8884d8" stopOpacity={0}/>
              </linearGradient>
            </defs>
            <XAxis dataKey="date" />
            <YAxis />
            <CartesianGrid strokeDasharray="3 3" />
            <Tooltip />
            <Area type="monotone" dataKey="requests" stroke="#8884d8" fillOpacity={1} fill="url(#colorRequests)" />
          </AreaChart>
        ) : activeView === "responseTimes" ? (
          <LineChart data={data}>
            <XAxis dataKey="date" />
            <YAxis />
            <CartesianGrid strokeDasharray="3 3" />
            <Tooltip />
            <Line type="monotone" dataKey="avgResponseTime" stroke="#82ca9d" />
          </LineChart>
        ) : (
          <LineChart data={data}>
            <XAxis dataKey="date" />
            <YAxis domain={[99.5, 100]} />
            <CartesianGrid strokeDasharray="3 3" />
            <Tooltip />
            <Line type="monotone" dataKey="successRate" stroke="#ff7300" />
          </LineChart>
        )}
      </ResponsiveContainer>
    </div>
  )
}