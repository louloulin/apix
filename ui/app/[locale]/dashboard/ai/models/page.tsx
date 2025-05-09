"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import { useTranslations, useLocale } from 'next-intl'

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import { Badge } from "@/components/ui/badge"
import {
  PlusCircle,
  Pencil,
  Trash2,
  RefreshCw,
  Search,
  Filter,
  Zap,
  Cpu,
  Server,
  Brain
} from "lucide-react"
import { useToast } from "@/components/ui/use-toast"
import { DataTable } from "@/components/ui/data-table"
import { useApiData, useApiMutation } from "@/lib/hooks/use-api-data"
import { aiModelsApi } from "@/lib/api-client"
import { AIModel } from "@/lib/api-client/ai-models"
import { ColumnDef } from "@tanstack/react-table"

export default function AIModelsPage() {
  const router = useRouter()
  const { toast } = useToast()
  const t = useTranslations('ai.models')
  const common = useTranslations('common')
  const locale = useLocale()
  const [activeTab, setActiveTab] = useState("all")
  const [providerFilter, setProviderFilter] = useState<string>("all")
  const [searchQuery, setSearchQuery] = useState("")

  // Fetch AI models data
  const {
    data: modelsData,
    isLoading,
    error,
    refetch,
    isRefetching
  } = useApiData(
    () => aiModelsApi.getModels(),
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

  // Enable/disable model mutation
  const {
    mutate: toggleModelStatus
  } = useApiMutation(
    async ({ model, enabled }: { model: AIModel, enabled: boolean }) => {
      if (enabled) {
        return aiModelsApi.enableModel(model.id)
      } else {
        return aiModelsApi.disableModel(model.id)
      }
    },
    {
      onSuccess: (data, { model, enabled }) => {
        toast({
          title: enabled ? common('enabled') : common('disabled'),
          description: `${model.name} ${enabled ? t('enabledSuccess') : t('disabledSuccess')}`
        })
        refetch()
      },
      onError: (error, { model, enabled }) => {
        toast({
          title: common('error'),
          description: `${enabled ? t('enableError') : t('disableError')}: ${error.message}`,
          variant: "destructive"
        })
      }
    }
  )

  // Delete model mutation
  const {
    mutate: deleteModel
  } = useApiMutation(
    async (model: AIModel) => {
      return aiModelsApi.deleteModel(model.id)
    },
    {
      onSuccess: (data, model) => {
        toast({
          title: common('success'),
          description: t('deleteSuccess', { name: model.name })
        })
        refetch()
      },
      onError: (error, model) => {
        toast({
          title: common('error'),
          description: `${t('deleteError')}: ${error.message}`,
          variant: "destructive"
        })
      }
    }
  )

  // Handle status toggle
  const handleStatusToggle = (model: AIModel) => {
    toggleModelStatus({ model, enabled: !model.enabled })
  }

  // Handle delete
  const handleDelete = (model: AIModel) => {
    if (window.confirm(t('deleteConfirm', { name: model.name }))) {
      deleteModel(model)
    }
  }

  // Filter models based on active tab and search query
  const filteredModels = modelsData?.models?.filter(model => {
    // Filter by tab
    if (activeTab === "enabled" && !model.enabled) return false
    if (activeTab === "disabled" && model.enabled) return false

    // Filter by provider
    if (providerFilter !== "all" && model.provider.toLowerCase() !== providerFilter) return false

    // Filter by search query
    if (searchQuery && !model.name.toLowerCase().includes(searchQuery.toLowerCase())) return false

    return true
  }) || []

  // Table columns
  const columns: ColumnDef<AIModel>[] = [
    {
      accessorKey: "name",
      header: common('name'),
      cell: ({ row }) => (
        <div className="font-medium">{row.original.name}</div>
      )
    },
    {
      accessorKey: "provider",
      header: t('provider'),
      cell: ({ row }) => (
        <Badge variant="outline">{row.original.provider}</Badge>
      )
    },
    {
      accessorKey: "maxTokens",
      header: t('maxTokens'),
      cell: ({ row }) => row.original.maxTokens?.toLocaleString() || "N/A"
    },
    {
      accessorKey: "priority",
      header: t('priority'),
      cell: ({ row }) => row.original.priority || 0
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
            onClick={() => router.push(`/${locale}/dashboard/ai/models/${row.original.id}/edit`)}
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
            <Button onClick={() => router.push(`/${locale}/dashboard/ai/models/create`)}>
              <PlusCircle className="mr-2 h-4 w-4" />
              {t('addModel')}
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
                <Select
                  defaultValue="all"
                  value={providerFilter}
                  onValueChange={(value) => setProviderFilter(value)}
                >
                  <SelectTrigger className="w-[180px]">
                    <Filter className="mr-2 h-4 w-4" />
                    <SelectValue placeholder={t('provider')} />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">All Providers</SelectItem>
                    <SelectItem value="openai">OpenAI</SelectItem>
                    <SelectItem value="anthropic">Anthropic</SelectItem>
                    <SelectItem value="google">Google</SelectItem>
                    <SelectItem value="azure">Azure</SelectItem>
                    <SelectItem value="custom">Custom</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <Tabs defaultValue="all" value={activeTab} onValueChange={setActiveTab}>
                <TabsList>
                  <TabsTrigger value="all">All</TabsTrigger>
                  <TabsTrigger value="enabled">{common('enabled')}</TabsTrigger>
                  <TabsTrigger value="disabled">{common('disabled')}</TabsTrigger>
                </TabsList>

                <TabsContent value="all" className="mt-4">
                  {isLoading ? (
                    <div className="flex justify-center py-8">
                      <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
                    </div>
                  ) : filteredModels.length === 0 ? (
                    <div className="text-center py-8 text-muted-foreground">
                      {common('noData')} {searchQuery && t('adjustSearch')}
                    </div>
                  ) : (
                    <DataTable
                      columns={columns}
                      data={filteredModels}
                      searchColumn="name"
                      searchPlaceholder={`${common('search')} ${t('title')}...`}
                    />
                  )}
                </TabsContent>

                {['enabled', 'disabled'].map(tabValue => (
                  <TabsContent key={tabValue} value={tabValue} className="mt-4">
                    {isLoading ? (
                      <div className="flex justify-center py-8">
                        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
                      </div>
                    ) : filteredModels.length === 0 ? (
                      <div className="text-center py-8 text-muted-foreground">
                        {t('no' + tabValue.charAt(0).toUpperCase() + tabValue.slice(1))}
                      </div>
                    ) : (
                      <DataTable
                        columns={columns}
                        data={filteredModels}
                        searchColumn="name"
                        searchPlaceholder={`${common('search')} ${t('title')}...`}
                      />
                    )}
                  </TabsContent>
                ))}
              </Tabs>
            </div>
          </CardContent>
        </Card>
      </div>
  )
}
