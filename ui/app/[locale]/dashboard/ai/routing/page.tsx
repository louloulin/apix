"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import { useTranslations } from 'next-intl'
import { I18nDashboardLayout } from "@/components/layout/i18n-dashboard-layout"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Switch } from "@/components/ui/switch"
import { Badge } from "@/components/ui/badge"
import { 
  PlusCircle, 
  Pencil, 
  Trash2, 
  RefreshCw, 
  Search,
  ArrowUpDown,
  Filter
} from "lucide-react"
import { useToast } from "@/components/ui/use-toast"
import { DataTable } from "@/components/ui/data-table"
import { useApiData, useApiMutation } from "@/lib/hooks/use-api-data"
import { aiModelsApi } from "@/lib/api-client"
import { AIRoutingRule } from "@/lib/api-client/ai-models"
import { ColumnDef } from "@tanstack/react-table"

export default function AIRoutingPage() {
  const router = useRouter()
  const { toast } = useToast()
  const t = useTranslations('ai.routing')
  const common = useTranslations('common')
  const [searchQuery, setSearchQuery] = useState("")

  // Fetch routing rules data
  const { 
    data: rulesData, 
    isLoading, 
    error, 
    refetch,
    isRefetching
  } = useApiData(
    () => aiModelsApi.getRoutingRules(),
    {
      onError: (err) => {
        toast({
          title: common('error'),
          description: `${t('fetchError')}: ${err.message}`,
          variant: "destructive"
        })
      }
    }
  )

  // Fetch models data for displaying model names
  const { 
    data: modelsData
  } = useApiData(
    () => aiModelsApi.getModels(),
    {
      onError: (err) => {
        console.error("Error fetching models:", err)
      }
    }
  )

  // Enable/disable rule mutation
  const { 
    mutate: toggleRuleStatus 
  } = useApiMutation(
    async ({ rule, enabled }: { rule: AIRoutingRule, enabled: boolean }) => {
      if (enabled) {
        return aiModelsApi.enableRoutingRule(rule.id)
      } else {
        return aiModelsApi.disableRoutingRule(rule.id)
      }
    },
    {
      onSuccess: (data, { rule, enabled }) => {
        toast({
          title: enabled ? common('enabled') : common('disabled'),
          description: `${rule.name} ${enabled ? t('enabledSuccess') : t('disabledSuccess')}`
        })
        refetch()
      },
      onError: (error, { rule, enabled }) => {
        toast({
          title: common('error'),
          description: `${enabled ? t('enableError') : t('disableError')}: ${error.message}`,
          variant: "destructive"
        })
      }
    }
  )

  // Delete rule mutation
  const { 
    mutate: deleteRule 
  } = useApiMutation(
    async (rule: AIRoutingRule) => {
      return aiModelsApi.deleteRoutingRule(rule.id)
    },
    {
      onSuccess: (data, rule) => {
        toast({
          title: common('success'),
          description: t('deleteSuccess', { name: rule.name })
        })
        refetch()
      },
      onError: (error, rule) => {
        toast({
          title: common('error'),
          description: `${t('deleteError')}: ${error.message}`,
          variant: "destructive"
        })
      }
    }
  )

  // Handle status toggle
  const handleStatusToggle = (rule: AIRoutingRule) => {
    toggleRuleStatus({ rule, enabled: !rule.enabled })
  }

  // Handle delete
  const handleDelete = (rule: AIRoutingRule) => {
    if (window.confirm(t('deleteConfirm', { name: rule.name }))) {
      deleteRule(rule)
    }
  }

  // Filter rules based on search query
  const filteredRules = rulesData?.rules?.filter(rule => {
    if (searchQuery && !rule.name.toLowerCase().includes(searchQuery.toLowerCase())) return false
    return true
  }) || []

  // Get model name from ID
  const getModelName = (modelId: string) => {
    const model = modelsData?.models?.find(m => m.id === modelId)
    return model ? model.name : modelId
  }

  // Table columns
  const columns: ColumnDef<AIRoutingRule>[] = [
    {
      accessorKey: "name",
      header: common('name'),
      cell: ({ row }) => (
        <div className="font-medium">{row.original.name}</div>
      )
    },
    {
      accessorKey: "priority",
      header: t('priority'),
      cell: ({ row }) => (
        <Badge variant="outline">{row.original.priority}</Badge>
      )
    },
    {
      accessorKey: "condition.type",
      header: t('condition'),
      cell: ({ row }) => (
        <div className="flex flex-col">
          <span>{row.original.condition.type}</span>
          <span className="text-xs text-muted-foreground truncate max-w-[200px]">
            {row.original.condition.pattern}
          </span>
        </div>
      )
    },
    {
      accessorKey: "targetModel",
      header: t('targetModel'),
      cell: ({ row }) => getModelName(row.original.targetModel)
    },
    {
      accessorKey: "enabled",
      header: common('status'),
      cell: ({ row }) => (
        <div className="flex items-center">
          <Switch 
            checked={row.original.enabled} 
            onCheckedChange={() => handleStatusToggle(row.original)}
          />
          <span className="ml-2">
            {row.original.enabled ? common('enabled') : common('disabled')}
          </span>
        </div>
      )
    },
    {
      id: "actions",
      header: common('actions'),
      cell: ({ row }) => (
        <div className="flex items-center gap-2">
          <Button 
            variant="ghost" 
            size="icon"
            onClick={() => router.push(`/dashboard/ai/routing/${row.original.id}/edit`)}
          >
            <Pencil className="h-4 w-4" />
          </Button>
          <Button 
            variant="ghost" 
            size="icon"
            onClick={() => handleDelete(row.original)}
          >
            <Trash2 className="h-4 w-4" />
          </Button>
        </div>
      )
    }
  ]

  return (
    <I18nDashboardLayout>
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
              onClick={() => refetch()}
              disabled={isRefetching}
            >
              <RefreshCw className={`h-4 w-4 ${isRefetching ? 'animate-spin' : ''}`} />
            </Button>
            <Button onClick={() => router.push('/dashboard/ai/routing/create')}>
              <PlusCircle className="mr-2 h-4 w-4" />
              {t('addRule')}
            </Button>
          </div>
        </div>

        <Card className="mt-6">
          <CardHeader>
            <CardTitle>{t('title')}</CardTitle>
            <CardDescription>
              {t('description')}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              <div className="flex items-center space-x-2">
                <div className="relative flex-1 max-w-sm">
                  <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
                  <Input
                    placeholder={`${common('search')}...`}
                    className="pl-8"
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                  />
                </div>
              </div>

              {isLoading ? (
                <div className="flex justify-center py-8">
                  <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
                </div>
              ) : filteredRules.length === 0 ? (
                <div className="text-center py-8 text-muted-foreground">
                  {common('noData')} {searchQuery && t('adjustSearch')}
                </div>
              ) : (
                <DataTable
                  columns={columns}
                  data={filteredRules}
                  searchColumn="name"
                  searchPlaceholder={`${common('search')} ${t('title')}...`}
                  initialSorting={[{ id: 'priority', desc: true }]}
                />
              )}
            </div>
          </CardContent>
        </Card>
      </div>
    </I18nDashboardLayout>
  )
}
