"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import { DashboardLayout } from "@/components/layout/dashboard-layout"
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
import { pluginApi } from "@/lib/api-client"
import { getPluginTypeDisplay, getPluginTypeColor, getStatusColor } from "@/lib/utils/plugins"
import { ColumnDef } from "@tanstack/react-table"
import { Plugin, PluginStatus } from "@/lib/api-client/plugins"

export default function PluginsPage() {
  const router = useRouter()
  const { toast } = useToast()
  const [activeTab, setActiveTab] = useState("all")
  const [statusFilter, setStatusFilter] = useState<string>("all")
  const [searchQuery, setSearchQuery] = useState("")

  // Fetch plugins data
  const { 
    data: pluginsData, 
    isLoading, 
    error, 
    refetch,
    isRefetching
  } = useApiData(
    () => pluginApi.getPlugins(),
    {
      onError: (err) => {
        toast({
          title: "Error fetching plugins",
          description: err.message,
          variant: "destructive"
        })
      }
    }
  )

  // Enable/disable plugin mutation
  const { 
    mutate: togglePluginStatus 
  } = useApiMutation(
    async ({ plugin, enabled }: { plugin: Plugin, enabled: boolean }) => {
      if (enabled) {
        return pluginApi.enablePlugin(plugin.id)
      } else {
        return pluginApi.disablePlugin(plugin.id)
      }
    },
    {
      onSuccess: (data, { plugin, enabled }) => {
        toast({
          title: `Plugin ${enabled ? 'enabled' : 'disabled'}`,
          description: `${plugin.id} has been ${enabled ? 'enabled' : 'disabled'}`
        })
        refetch()
      },
      onError: (error, { plugin, enabled }) => {
        toast({
          title: "Error",
          description: `Failed to ${enabled ? 'enable' : 'disable'} plugin: ${error.message}`,
          variant: "destructive"
        })
      }
    }
  )

  // Delete plugin mutation
  const { 
    mutate: deletePlugin 
  } = useApiMutation(
    async (plugin: Plugin) => {
      return pluginApi.deletePlugin(plugin.id)
    },
    {
      onSuccess: (data, plugin) => {
        toast({
          title: "Plugin deleted",
          description: `${plugin.id} has been deleted`
        })
        refetch()
      },
      onError: (error, plugin) => {
        toast({
          title: "Error",
          description: `Failed to delete plugin: ${error.message}`,
          variant: "destructive"
        })
      }
    }
  )

  // Reload plugin mutation
  const { 
    mutate: reloadPlugin 
  } = useApiMutation(
    async (plugin: Plugin) => {
      return pluginApi.reloadPlugin(plugin.id)
    },
    {
      onSuccess: (data, plugin) => {
        toast({
          title: "Plugin reloaded",
          description: `${plugin.id} has been reloaded`
        })
        refetch()
      },
      onError: (error, plugin) => {
        toast({
          title: "Error",
          description: `Failed to reload plugin: ${error.message}`,
          variant: "destructive"
        })
      }
    }
  )

  // Handle plugin status toggle
  const handleStatusToggle = (plugin: Plugin, enabled: boolean) => {
    togglePluginStatus({ plugin, enabled })
  }

  // Handle plugin deletion
  const handleDelete = (plugin: Plugin) => {
    if (window.confirm(`Are you sure you want to delete the plugin ${plugin.id}?`)) {
      deletePlugin(plugin)
    }
  }

  // Handle plugin reload
  const handleReload = (plugin: Plugin) => {
    reloadPlugin(plugin)
  }

  // Filter plugins based on active tab, status filter, and search query
  const plugins = pluginsData?.plugins || []
  const filteredPlugins = plugins
    .filter(plugin => activeTab === "all" || plugin.type === activeTab)
    .filter(plugin => statusFilter === "all" || plugin.status === statusFilter)
    .filter(plugin => {
      if (!searchQuery) return true
      const query = searchQuery.toLowerCase()
      return (
        plugin.id.toLowerCase().includes(query) ||
        plugin.type.toLowerCase().includes(query)
      )
    })

  // Define table columns
  const columns: ColumnDef<Plugin>[] = [
    {
      accessorKey: "id",
      header: "ID",
      cell: ({ row }) => <span className="font-medium">{row.original.id}</span>
    },
    {
      accessorKey: "type",
      header: "Type",
      cell: ({ row }) => {
        const type = row.original.type
        const color = getPluginTypeColor(type)
        return (
          <Badge variant="outline" className={`bg-${color}-100 text-${color}-800 dark:bg-${color}-900 dark:text-${color}-300 border-${color}-200`}>
            {getPluginTypeDisplay(type)}
          </Badge>
        )
      }
    },
    {
      accessorKey: "version",
      header: "Version",
      cell: ({ row }) => row.original.version || "1.0.0"
    },
    {
      accessorKey: "status",
      header: "Status",
      cell: ({ row }) => {
        const plugin = row.original
        const color = getStatusColor(plugin.status)
        return (
          <div className="flex items-center space-x-2">
            <Switch
              checked={plugin.status === 'enabled'}
              onCheckedChange={(checked) => handleStatusToggle(plugin, checked)}
              id={`plugin-status-${plugin.id}`}
            />
            <Badge variant="outline" className={`bg-${color}-100 text-${color}-800 dark:bg-${color}-900 dark:text-${color}-300 border-${color}-200`}>
              {plugin.status}
            </Badge>
          </div>
        )
      }
    },
    {
      id: "actions",
      header: "Actions",
      cell: ({ row }) => {
        const plugin = row.original
        return (
          <div className="flex justify-end space-x-1">
            <Button variant="ghost" size="icon" onClick={() => router.push(`/dashboard/plugins/${plugin.id}/edit`)}>
              <Pencil className="h-4 w-4" />
              <span className="sr-only">Edit</span>
            </Button>
            <Button variant="ghost" size="icon" onClick={() => handleReload(plugin)}>
              <RefreshCw className="h-4 w-4" />
              <span className="sr-only">Reload</span>
            </Button>
            <Button variant="ghost" size="icon" onClick={() => handleDelete(plugin)}>
              <Trash2 className="h-4 w-4" />
              <span className="sr-only">Delete</span>
            </Button>
          </div>
        )
      }
    }
  ]

  // Get plugin type icon
  const getPluginTypeIcon = (type: string) => {
    switch (type.toLowerCase()) {
      case 'authentication':
      case 'auth':
        return <Key className="h-4 w-4" />
      case 'security':
        return <Shield className="h-4 w-4" />
      case 'transformation':
        return <FileCode className="h-4 w-4" />
      case 'business-logic':
        return <Code className="h-4 w-4" />
      case 'caching':
        return <Database className="h-4 w-4" />
      default:
        return <Code className="h-4 w-4" />
    }
  }

  return (
    <DashboardLayout>
      <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold">Plugins Management</h1>
            <p className="text-muted-foreground">
              Configure and manage your API Gateway plugins
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
            <Button onClick={() => router.push('/dashboard/plugins/create')}>
              <PlusCircle className="mr-2 h-4 w-4" />
              Install Plugin
            </Button>
          </div>
        </div>

        <Card className="mt-6">
          <CardHeader>
            <CardTitle>Plugins</CardTitle>
            <CardDescription>
              Manage and configure your API Gateway plugins
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              <div className="flex items-center space-x-2">
                <div className="relative flex-1 max-w-sm">
                  <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
                  <Input
                    placeholder="Search plugins..."
                    className="pl-8"
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                  />
                </div>
                <Select
                  defaultValue="all"
                  value={statusFilter}
                  onValueChange={(value) => setStatusFilter(value)}
                >
                  <SelectTrigger className="w-[180px]">
                    <Filter className="mr-2 h-4 w-4" />
                    <SelectValue placeholder="Status" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">All Statuses</SelectItem>
                    <SelectItem value="enabled">Enabled</SelectItem>
                    <SelectItem value="disabled">Disabled</SelectItem>
                    <SelectItem value="error">Error</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <Tabs defaultValue="all" className="w-full" onValueChange={setActiveTab}>
                <TabsList>
                  <TabsTrigger value="all" className="flex items-center gap-2">
                    <Code className="h-4 w-4" />
                    All Plugins
                  </TabsTrigger>
                  <TabsTrigger value="authentication" className="flex items-center gap-2">
                    <Key className="h-4 w-4" />
                    Authentication
                  </TabsTrigger>
                  <TabsTrigger value="security" className="flex items-center gap-2">
                    <Shield className="h-4 w-4" />
                    Security
                  </TabsTrigger>
                  <TabsTrigger value="transformation" className="flex items-center gap-2">
                    <FileCode className="h-4 w-4" />
                    Transformation
                  </TabsTrigger>
                  <TabsTrigger value="business-logic" className="flex items-center gap-2">
                    <Code className="h-4 w-4" />
                    Business Logic
                  </TabsTrigger>
                </TabsList>

                <TabsContent value="all" className="mt-4">
                  {isLoading ? (
                    <div className="flex justify-center py-8">
                      <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
                    </div>
                  ) : filteredPlugins.length === 0 ? (
                    <div className="text-center py-8 text-muted-foreground">
                      No plugins found. {searchQuery && "Try adjusting your search."}
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
    </DashboardLayout>
  )
}
