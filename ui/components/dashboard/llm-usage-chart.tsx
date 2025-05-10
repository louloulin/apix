"use client";

import { useState, useMemo } from "react";
import { useTranslations } from "next-intl";
import {
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
} from "recharts";
import { LlmUsageDataPoint } from "@/lib/api-client/dashboard";

// 当没有数据时显示的空数据
const emptyData = [
  { name: "No Data", requests: 0, tokens: 0, color: "#cccccc" }
]

interface LabelProps {
  cx: number;
  cy: number;
  midAngle: number;
  innerRadius: number;
  outerRadius: number;
  percent: number;
  index: number;
}

interface LlmUsageChartProps {
  data: LlmUsageDataPoint[]
}

export function LlmUsageChart({ data: externalData }: LlmUsageChartProps) {
  const t = useTranslations('dashboard')
  const [activeView, setActiveView] = useState("requests")

  // 使用外部数据或空数据
  const data = useMemo(() => {
    return externalData && externalData.length > 0 ? externalData : emptyData
  }, [externalData])

  const RADIAN = Math.PI / 180
  const renderCustomizedLabel = ({ cx, cy, midAngle, innerRadius, outerRadius, percent, index }: LabelProps) => {
    const radius = innerRadius + (outerRadius - innerRadius) * 0.5
    const x = cx + radius * Math.cos(-midAngle * RADIAN)
    const y = cy + radius * Math.sin(-midAngle * RADIAN)

    return (
      <text
        x={x}
        y={y}
        fill="white"
        textAnchor={x > cx ? 'start' : 'end'}
        dominantBaseline="central"
      >
        {`${(percent * 100).toFixed(0)}%`}
      </text>
    )
  }

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
          onClick={() => setActiveView("tokens")}
          className={`px-3 py-1 text-sm rounded-md ${activeView === "tokens"
            ? "bg-primary text-primary-foreground"
            : "bg-secondary text-secondary-foreground"}`}
        >
          {t('totalTokens')}
        </button>
        <button
          onClick={() => setActiveView("distribution")}
          className={`px-3 py-1 text-sm rounded-md ${activeView === "distribution"
            ? "bg-primary text-primary-foreground"
            : "bg-secondary text-secondary-foreground"}`}
        >
          {t('requestDistribution')}
        </button>
      </div>

      <ResponsiveContainer width="100%" height={250}>
        {activeView === "requests" ? (
          <BarChart data={data}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="name" />
            <YAxis />
            <Tooltip />
            <Bar dataKey="requests" fill="#8884d8">
              {data.map((entry, index) => (
                <Cell key={`cell-${index}`} fill={entry.color} />
              ))}
            </Bar>
          </BarChart>
        ) : activeView === "tokens" ? (
          <BarChart data={data}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="name" />
            <YAxis />
            <Tooltip />
            <Bar dataKey="tokens" fill="#82ca9d">
              {data.map((entry, index) => (
                <Cell key={`cell-${index}`} fill={entry.color} />
              ))}
            </Bar>
          </BarChart>
        ) : (
          <PieChart>
            <Pie
              data={data}
              cx="50%"
              cy="50%"
              labelLine={false}
              label={renderCustomizedLabel}
              outerRadius={100}
              fill="#8884d8"
              dataKey="requests"
            >
              {data.map((entry, index) => (
                <Cell key={`cell-${index}`} fill={entry.color} />
              ))}
            </Pie>
            <Tooltip />
            <Legend />
          </PieChart>
        )}
      </ResponsiveContainer>
    </div>
  )
}