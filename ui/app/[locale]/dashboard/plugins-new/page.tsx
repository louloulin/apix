"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import { useTranslations, useLocale } from 'next-intl'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Switch } from "@/components/ui/switch"
import { Badge } from "@/components/ui/badge"
import { 
  PlusCircle, 
  Pencil, 
  Trash2, 
  RefreshCw, 
  Filter, 
  Search,
  Shield,
  Key,
  FileCode,
  Code,
  Database
} from "lucide-react"
import { useToast } from "@/components/ui/use-toast"
import { DataTable } from "@/components/ui/data-table"
import { useApiData, useApiMutation } from "@/lib/hooks/use-api-data"
import { getPlugins, enablePlugin, disablePlugin, deletePlugin, reloadPlugin } from "@/lib/api/plugins"
import { getPluginTypeDisplay, getPluginTypeColor, getStatusColor } from "@/lib/utils/plugins"
import { Plugin } from "@/lib/api/plugins"
import { ColumnDef } from "@tanstack/react-table"

export default function PluginsNewPage() {
  const router = useRouter()
  const locale = useLocale()
  const { toast } = useToast()
  const t = useTranslations('plugins')
  const common = useTranslations('common')
  
  const [activeTab, setActiveTab] = useState("all")
  const [searchQuery, setSearchQuery] = useState("")

  // Fetch plugins data
  const {
    data: pluginsData,
    isLoading,
    error,
    refetch,
    isRefetching
  } = useApiData(
    () => getPlugins(),
    {
      onError: (error) => {
        toast({
          title: common('error'),
          description: error.message,
          variant: "destructive"
        })
      }
    }
  )

  // Toggle plugin status mutation
  const { mutate: togglePluginStatus } = useApiMutation(
    async ({ plugin, enabled }: { plugin: Plugin, enabled: boolean }) => {
      return enabled ? enablePlugin(plugin.id) : disablePlugin(plugin.id)
    },
    {
      onSuccess: (data, { plugin, enabled }) => {
        toast({
          title: common('success'),
          description: `${plugin.id} ${enabled ? t('enabledSuccess') : t('disabledSuccess')}`
        })
        refetch()
      },
      onError: (error, { plugin, enabled }) => {
        toast({
          title: common('error'),
          description: `${enabled ? t('enableError') : t('disableError')}: ${error.message}`,
          variant: "destructive"
        })
      }
    }
  )

  // Delete plugin mutation
  const { mutate: deletePluginMutation } = useApiMutation(
    async (plugin: Plugin) => {
      if (!confirm(t('deleteConfirm', { name: plugin.id }))) {
        throw new Error("Deletion cancelled")
      }
      return deletePlugin(plugin.id)
    },
    {
      onSuccess: (data, plugin) => {
        toast({
          title: common('success'),
          description: `${plugin.id} ${t('deleteSuccess')}`
        })
        refetch()
      },
      onError: (error, plugin) => {
        if (error.message === "Deletion cancelled") return
        
        toast({
          title: common('error'),
          description: `${t('deleteError')}: ${error.message}`,
          variant: "destructive"
        })
      }
    }
  )

  // Reload plugin mutation
  const { mutate: reloadPluginMutation } = useApiMutation(
    async (plugin: Plugin) => {
      return reloadPlugin(plugin.id)
    },
    {
      onSuccess: (data, plugin) => {
        toast({
          title: common('success'),
          description: `${plugin.id} reloaded successfully`
        })
        refetch()
      },
      onError: (error, plugin) => {
        toast({
          title: common('error'),
          description: `Failed to reload plugin: ${error.message}`,
          variant: "destructive"
        })
      }
    }
  )

  // Handle status toggle
  const handleStatusToggle = (plugin: Plugin) => {
    togglePluginStatus({ plugin, enabled: !plugin.status.includes('enabled') })
  }

  // Handle delete
  const handleDelete = (plugin: Plugin) => {
    deletePluginMutation(plugin)
  }

  // Handle reload
  const handleReload = (plugin: Plugin) => {
    reloadPluginMutation(plugin)
  }

  // Filter plugins based on active tab
  const plugins = pluginsData?.data || []
  const filteredPlugins = plugins.filter(plugin => {
    return activeTab === "all" || plugin.type === activeTab
  })

  // Define table columns
  const columns: ColumnDef<Plugin>[] = [
    {
      accessorKey: "id",
      header: "ID",
      cell: ({ row }) => <div className="font-medium">{row.original.id}</div>
    },
    {
      accessorKey: "type",
      header: common('type'),
      cell: ({ row }) => (
        <Badge variant="outline" className={`bg-${getPluginTypeColor(row.original.type)}-100 text-${getPluginTypeColor(row.original.type)}-800 dark:bg-${getPluginTypeColor(row.original.type)}-900 dark:text-${getPluginTypeColor(row.original.type)}-300 border-${getPluginTypeColor(row.original.type)}-200`}>
          {getPluginTypeDisplay(row.original.type)}
        </Badge>
      )
    },
    {
      accessorKey: "version",
      header: t('version'),
      cell: ({ row }) => row.original.version || "N/A"
    },
    {
      accessorKey: "status",
      header: common('status'),
      cell: ({ row }) => (
        <div className="flex items-center">
          <Switch 
            checked={row.original.status === 'enabled'} 
            onCheckedChange={() => handleStatusToggle(row.original)}
          />
          <span className="ml-2">
            {row.original.status === 'enabled' ? common('enabled') : common('disabled')}
          </span>
        </div>
      )
    },
    {
      id: "actions",
      header: common('actions'),
      cell: ({ row }) => {
        const plugin = row.original
        return (
          <div className="flex justify-end space-x-1">
            <Button variant="ghost" size="icon" onClick={() => router.push(`/${locale}/dashboard/plugins/${plugin.id}/edit`)}>
              <Pencil className="h-4 w-4" />
              <span className="sr-only">{common('edit')}</span>
            </Button>
            <Button variant="ghost" size="icon" onClick={() => handleReload(plugin)}>
              <RefreshCw className="h-4 w-4" />
              <span className="sr-only">{common('refresh')}</span>
            </Button>
            <Button variant="ghost" size="icon" onClick={() => handleDelete(plugin)}>
              <Trash2 className="h-4 w-4" />
              <span className="sr-only">{common('delete')}</span>
            </Button>
          </div>
        )
      }
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
          <Button onClick={() => router.push(`/${locale}/dashboard/plugins/create`)}>
            <PlusCircle className="mr-2 h-4 w-4" />
            {t('addPlugin')}
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
            <Tabs defaultValue="all" value={activeTab} onValueChange={setActiveTab}>
              <TabsList className="grid grid-cols-5 mb-4">
                <TabsTrigger value="all">All</TabsTrigger>
                <TabsTrigger value="authentication">Authentication</TabsTrigger>
                <TabsTrigger value="security">Security</TabsTrigger>
                <TabsTrigger value="transformation">Transformation</TabsTrigger>
                <TabsTrigger value="business-logic">Business Logic</TabsTrigger>
              </TabsList>
              
              <TabsContent value="all" className="mt-4">
                {isLoading ? (
                  <div className="flex justify-center py-8">
                    <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
                  </div>
                ) : filteredPlugins.length === 0 ? (
                  <div className="text-center py-8 text-muted-foreground">
                    {common('noData')}
                  </div>
                ) : (
                  <DataTable
                    columns={columns}
                    data={filteredPlugins}
                    searchColumn="id"
                    searchPlaceholder={`${common('search')} ${t('title')}...`}
                  />
                )}
              </TabsContent>
              
              {['authentication', 'security', 'transformation', 'business-logic'].map(tabValue => (
                <TabsContent key={tabValue} value={tabValue} className="mt-4">
                  {isLoading ? (
                    <div className="flex justify-center py-8">
                      <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
                    </div>
                  ) : filteredPlugins.length === 0 ? (
                    <div className="text-center py-8 text-muted-foreground">
                      No {tabValue} plugins found.
                    </div>
                  ) : (
                    <DataTable
                      columns={columns}
                      data={filteredPlugins}
                      searchColumn="id"
                      searchPlaceholder="Filter by ID..."
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
