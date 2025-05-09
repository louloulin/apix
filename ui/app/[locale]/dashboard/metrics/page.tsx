"use client"

import { useState } from "react"
import { useTranslations } from 'next-intl'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { AlertCircle, RefreshCw, Cpu, HardDrive } from "lucide-react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"

// Data card component for displaying metrics
interface DataCardProps {
  title: string
  value: string | number
  description?: string
  icon?: React.ReactNode
}

function DataCard({ title, value, description, icon }: DataCardProps) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
        <CardTitle className="text-sm font-medium">
          {title}
        </CardTitle>
        {icon}
      </CardHeader>
      <CardContent>
        <div className="text-2xl font-bold">{value}</div>
        {description && (
          <p className="text-xs text-muted-foreground">
            {description}
          </p>
        )}
      </CardContent>
    </Card>
  )
}

export default function MetricsPage() {
  const t = useTranslations('metrics')
  const common = useTranslations('common')

  const [activeTab, setActiveTab] = useState("overview")
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<Error | null>(null)

  // Mock data
  const mockMetricsData = {
    cpu: {
      cores: 8,
      processCpuLoad: 0.23,
      systemLoad: 2.15
    },
    memory: {
      heap: {
        used: 187699728,
        max: 4294967296
      }
    },
    threads: {
      count: 32,
      peakCount: 36
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">{t('title')}</h1>
          <p className="text-muted-foreground">
            {t('description')}
          </p>
        </div>
        <div className="flex gap-2">
          <Button
            variant="outline"
            size="icon"
          >
            <RefreshCw className="h-4 w-4" />
          </Button>
        </div>
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertTitle>{common('error')}</AlertTitle>
          <AlertDescription>{error.message}</AlertDescription>
        </Alert>
      )}

      <div className="grid gap-4 md:grid-cols-3">
        <DataCard
          title={t('cpuUsage')}
          value={`${(mockMetricsData.cpu.processCpuLoad * 100).toFixed(2)}%`}
          description={`${mockMetricsData.cpu.cores} ${t('coresAvailable')}`}
          icon={<Cpu className="h-4 w-4" />}
        />

        <DataCard
          title={t('memoryUsage')}
          value={`${((mockMetricsData.memory.heap.used / mockMetricsData.memory.heap.max) * 100).toFixed(2)}%`}
          description={`${mockMetricsData.memory.heap.used} / ${mockMetricsData.memory.heap.max}`}
          icon={<Cpu className="h-4 w-4" />}
        />

        <DataCard
          title={t('threadCount')}
          value={mockMetricsData.threads.count}
          description={`${t('peak')}: ${mockMetricsData.threads.peakCount}`}
          icon={<HardDrive className="h-4 w-4" />}
        />
      </div>

      <Card className="mt-4">
        <CardHeader>
          <CardTitle>{t('systemOverview')}</CardTitle>
          <CardDescription>
            {t('systemOverviewDescription')}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <h4 className="text-sm font-medium mb-1">{t('cpuUsage')}</h4>
                <p className="text-sm">{(mockMetricsData.cpu.processCpuLoad * 100).toFixed(2)}%</p>
              </div>

              <div>
                <h4 className="text-sm font-medium mb-1">{t('memoryUsage')}</h4>
                <p className="text-sm">{((mockMetricsData.memory.heap.used / mockMetricsData.memory.heap.max) * 100).toFixed(2)}%</p>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
