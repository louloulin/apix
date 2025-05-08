"use client"

import { useState, useEffect } from "react"
import { useRouter } from "next/navigation"
import { DashboardLayout } from "@/components/layout/dashboard-layout"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Switch } from "@/components/ui/switch"
import { Badge } from "@/components/ui/badge"
import { PlusIcon, PencilIcon, TrashIcon, RefreshCwIcon } from "lucide-react"
import { toast } from "@/components/ui/use-toast"
import { getPlugins, enablePlugin, disablePlugin, deletePlugin, reloadPlugin } from "@/lib/api/plugins"
import { getPluginTypeDisplay, getPluginTypeColor, getStatusColor } from "@/lib/utils/plugins"

// Import the Plugin type from our API client
import { Plugin, PluginStatus } from "@/lib/api/plugins"

export default function PluginsPage() {
  const router = useRouter()
  const [activeTab, setActiveTab] = useState("all")
  const [statusFilter, setStatusFilter] = useState("all")
  const [searchQuery, setSearchQuery] = useState("")
  const [plugins, setPlugins] = useState<Plugin[]>([])
  const [loading, setLoading] = useState(true)

  // Fetch plugins from API
  useEffect(() => {
    async function fetchPlugins() {
      setLoading(true)
      try {
        const response = await getPlugins()
        if (response.success) {
          setPlugins(response.data)
        } else {
          toast({
            title: "Error fetching plugins",
            description: response.error,
            variant: "destructive"
          })
        }
      } catch (error) {
        toast({
          title: "Error",
          description: "Failed to fetch plugins",
          variant: "destructive"
        })
      } finally {
        setLoading(false)
      }
    }

    fetchPlugins()
  }, [])

  // Handle plugin status toggle
  const handleStatusToggle = async (plugin: Plugin, enabled: boolean) => {
    try {
      const response = enabled
        ? await enablePlugin(plugin.id)
        : await disablePlugin(plugin.id)

      if (response.success) {
        // Update local state
        setPlugins(plugins.map(p =>
          p.id === plugin.id
            ? { ...p, status: enabled ? 'enabled' as PluginStatus : 'disabled' as PluginStatus }
            : p
        ))

        toast({
          title: `Plugin ${enabled ? 'enabled' : 'disabled'}`,
          description: `${plugin.id} has been ${enabled ? 'enabled' : 'disabled'}`
        })
      } else {
        toast({
          title: "Error",
          description: response.error,
          variant: "destructive"
        })
      }
    } catch (error) {
      toast({
        title: "Error",
        description: `Failed to ${enabled ? 'enable' : 'disable'} plugin`,
        variant: "destructive"
      })
    }
  }

  // Handle plugin deletion
  const handleDelete = async (plugin: Plugin) => {
    if (!confirm(`Are you sure you want to delete the plugin ${plugin.id}?`)) {
      return
    }

    try {
      const response = await deletePlugin(plugin.id)

      if (response.success) {
        // Remove from local state
        setPlugins(plugins.filter(p => p.id !== plugin.id))

        toast({
          title: "Plugin deleted",
          description: `${plugin.id} has been deleted`
        })
      } else {
        toast({
          title: "Error",
          description: response.error,
          variant: "destructive"
        })
      }
    } catch (error) {
      toast({
        title: "Error",
        description: "Failed to delete plugin",
        variant: "destructive"
      })
    }
  }

  // Handle plugin reload
  const handleReload = async (plugin: Plugin) => {
    try {
      const response = await reloadPlugin(plugin.id)

      if (response.success) {
        toast({
          title: "Plugin reloaded",
          description: `${plugin.id} has been reloaded`
        })

        // Refresh the plugins list
        const pluginsResponse = await getPlugins()
        if (pluginsResponse.success) {
          setPlugins(pluginsResponse.data)
        }
      } else {
        toast({
          title: "Error",
          description: response.error,
          variant: "destructive"
        })
      }
    } catch (error) {
      toast({
        title: "Error",
        description: "Failed to reload plugin",
        variant: "destructive"
      })
    }
  }

  // Filter plugins based on active tab, status filter, and search query
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
          <Button onClick={() => router.push('/dashboard/plugins/create')}>
            <PlusIcon className="mr-2 h-4 w-4" />
            Install Plugin
          </Button>
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
                <Input
                  placeholder="Search plugins..."
                  className="max-w-sm"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                />
                <Select
                  defaultValue="all"
                  onValueChange={(value) => setStatusFilter(value)}
                >
                  <SelectTrigger className="w-[180px]">
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
                  <TabsTrigger value="all">All Plugins</TabsTrigger>
                  <TabsTrigger value="authentication">Authentication</TabsTrigger>
                  <TabsTrigger value="security">Security</TabsTrigger>
                  <TabsTrigger value="transformation">Transformation</TabsTrigger>
                  <TabsTrigger value="business-logic">Business Logic</TabsTrigger>
                </TabsList>

                <TabsContent value="all" className="mt-4">
                  {loading ? (
                    <div className="flex justify-center py-8">
                      <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
                    </div>
                  ) : filteredPlugins.length === 0 ? (
                    <div className="text-center py-8 text-muted-foreground">
                      No plugins found. {searchQuery && "Try adjusting your search."}
                    </div>
                  ) : (
                    <PluginsList
                      plugins={filteredPlugins}
                      onToggleStatus={handleStatusToggle}
                      onDelete={handleDelete}
                      onReload={handleReload}
                      onEdit={(plugin) => router.push(`/dashboard/plugins/${plugin.id}/edit`)}
                    />
                  )}
                </TabsContent>
                {['authentication', 'security', 'transformation', 'business-logic'].map(tabValue => (
                  <TabsContent key={tabValue} value={tabValue} className="mt-4">
                    {loading ? (
                      <div className="flex justify-center py-8">
                        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
                      </div>
                    ) : filteredPlugins.length === 0 ? (
                      <div className="text-center py-8 text-muted-foreground">
                        No {tabValue} plugins found.
                      </div>
                    ) : (
                      <PluginsList
                        plugins={filteredPlugins}
                        onToggleStatus={handleStatusToggle}
                        onDelete={handleDelete}
                        onReload={handleReload}
                        onEdit={(plugin) => router.push(`/dashboard/plugins/${plugin.id}/edit`)}
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

// Plugins list component
interface PluginsListProps {
  plugins: Plugin[]
  onToggleStatus: (plugin: Plugin, enabled: boolean) => void
  onDelete: (plugin: Plugin) => void
  onReload: (plugin: Plugin) => void
  onEdit: (plugin: Plugin) => void
}

function PluginsList({ plugins, onToggleStatus, onDelete, onReload, onEdit }: PluginsListProps) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>ID</TableHead>
          <TableHead>Type</TableHead>
          <TableHead>Version</TableHead>
          <TableHead>Status</TableHead>
          <TableHead className="text-right">Actions</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {plugins.map(plugin => (
          <TableRow key={plugin.id}>
            <TableCell className="font-medium">{plugin.id}</TableCell>
            <TableCell>
              <Badge variant="outline" className={`bg-${getPluginTypeColor(plugin.type)}-100 text-${getPluginTypeColor(plugin.type)}-800 dark:bg-${getPluginTypeColor(plugin.type)}-900 dark:text-${getPluginTypeColor(plugin.type)}-300 border-${getPluginTypeColor(plugin.type)}-200`}>
                {getPluginTypeDisplay(plugin.type)}
              </Badge>
            </TableCell>
            <TableCell>{plugin.version || '1.0.0'}</TableCell>
            <TableCell>
              <div className="flex items-center space-x-2">
                <Switch
                  checked={plugin.status === 'enabled'}
                  onCheckedChange={(checked) => onToggleStatus(plugin, checked)}
                  id={`plugin-status-${plugin.id}`}
                />
                <Badge variant="outline" className={`bg-${getStatusColor(plugin.status)}-100 text-${getStatusColor(plugin.status)}-800 dark:bg-${getStatusColor(plugin.status)}-900 dark:text-${getStatusColor(plugin.status)}-300 border-${getStatusColor(plugin.status)}-200`}>
                  {plugin.status}
                </Badge>
              </div>
            </TableCell>
            <TableCell className="text-right">
              <div className="flex justify-end space-x-1">
                <Button variant="ghost" size="icon" onClick={() => onEdit(plugin)}>
                  <PencilIcon className="h-4 w-4" />
                  <span className="sr-only">Edit</span>
                </Button>
                <Button variant="ghost" size="icon" onClick={() => onReload(plugin)}>
                  <RefreshCwIcon className="h-4 w-4" />
                  <span className="sr-only">Reload</span>
                </Button>
                <Button variant="ghost" size="icon" onClick={() => onDelete(plugin)}>
                  <TrashIcon className="h-4 w-4" />
                  <span className="sr-only">Delete</span>
                </Button>
              </div>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}