"use client"

import { useState, useEffect } from "react"
import { useRouter } from "next/navigation"
import { DashboardLayout } from "@/components/layout/dashboard-layout"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Switch } from "@/components/ui/switch"
import { toast } from "@/components/ui/use-toast"
import { ArrowLeftIcon, PencilIcon, TrashIcon, RefreshCwIcon } from "lucide-react"
import { getPlugin, enablePlugin, disablePlugin, deletePlugin, reloadPlugin } from "@/lib/api/plugins"
import { getPluginTypeDisplay, getPluginTypeColor, getStatusColor, formatJson } from "@/lib/utils/plugins"
import { Plugin } from "@/lib/api/plugins"

export default function PluginDetailPage({ params }: { params: { id: string } }) {
  const router = useRouter()
  const [plugin, setPlugin] = useState<Plugin | null>(null)
  const [loading, setLoading] = useState(true)
  const [activeTab, setActiveTab] = useState("overview")

  // Fetch plugin details
  useEffect(() => {
    async function fetchPlugin() {
      setLoading(true)
      try {
        const response = await getPlugin(params.id)
        if (response.success) {
          setPlugin(response.data)
        } else {
          toast({
            title: "Error fetching plugin",
            description: response.error,
            variant: "destructive"
          })
        }
      } catch (error) {
        toast({
          title: "Error",
          description: "Failed to fetch plugin details",
          variant: "destructive"
        })
      } finally {
        setLoading(false)
      }
    }
    
    fetchPlugin()
  }, [params.id])

  // Handle plugin status toggle
  const handleStatusToggle = async (enabled: boolean) => {
    if (!plugin) return
    
    try {
      const response = enabled 
        ? await enablePlugin(plugin.id)
        : await disablePlugin(plugin.id)
      
      if (response.success) {
        // Update local state
        setPlugin({
          ...plugin,
          status: enabled ? 'enabled' : 'disabled'
        })
        
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
  const handleDelete = async () => {
    if (!plugin) return
    
    if (!confirm(`Are you sure you want to delete the plugin ${plugin.id}?`)) {
      return
    }
    
    try {
      const response = await deletePlugin(plugin.id)
      
      if (response.success) {
        toast({
          title: "Plugin deleted",
          description: `${plugin.id} has been deleted`
        })
        
        // Navigate back to plugins list
        router.push('/dashboard/plugins')
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
  const handleReload = async () => {
    if (!plugin) return
    
    try {
      const response = await reloadPlugin(plugin.id)
      
      if (response.success) {
        toast({
          title: "Plugin reloaded",
          description: `${plugin.id} has been reloaded`
        })
        
        // Refresh the plugin details
        const pluginResponse = await getPlugin(plugin.id)
        if (pluginResponse.success) {
          setPlugin(pluginResponse.data)
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

  if (loading) {
    return (
      <DashboardLayout>
        <div className="flex justify-center items-center h-[calc(100vh-200px)]">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary"></div>
        </div>
      </DashboardLayout>
    )
  }

  if (!plugin) {
    return (
      <DashboardLayout>
        <div className="flex flex-col items-center justify-center h-[calc(100vh-200px)]">
          <h2 className="text-2xl font-bold mb-2">Plugin Not Found</h2>
          <p className="text-muted-foreground mb-4">The plugin you're looking for doesn't exist or has been deleted.</p>
          <Button onClick={() => router.push('/dashboard/plugins')}>
            <ArrowLeftIcon className="mr-2 h-4 w-4" />
            Back to Plugins
          </Button>
        </div>
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout>
      <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={() => router.push('/dashboard/plugins')}>
              <ArrowLeftIcon className="mr-2 h-4 w-4" />
              Back
            </Button>
            <h1 className="text-3xl font-bold">{plugin.id}</h1>
            <Badge variant="outline" className={`bg-${getPluginTypeColor(plugin.type)}-100 text-${getPluginTypeColor(plugin.type)}-800 dark:bg-${getPluginTypeColor(plugin.type)}-900 dark:text-${getPluginTypeColor(plugin.type)}-300 border-${getPluginTypeColor(plugin.type)}-200 ml-2`}>
              {getPluginTypeDisplay(plugin.type)}
            </Badge>
            <Badge variant="outline" className={`bg-${getStatusColor(plugin.status)}-100 text-${getStatusColor(plugin.status)}-800 dark:bg-${getStatusColor(plugin.status)}-900 dark:text-${getStatusColor(plugin.status)}-300 border-${getStatusColor(plugin.status)}-200`}>
              {plugin.status}
            </Badge>
          </div>
          <div className="flex items-center gap-2">
            <div className="flex items-center mr-4">
              <Switch 
                checked={plugin.status === 'enabled'}
                onCheckedChange={handleStatusToggle}
                id="plugin-status"
                className="mr-2"
              />
              <span>{plugin.status === 'enabled' ? 'Enabled' : 'Disabled'}</span>
            </div>
            <Button variant="outline" size="sm" onClick={() => router.push(`/dashboard/plugins/${plugin.id}/edit`)}>
              <PencilIcon className="mr-2 h-4 w-4" />
              Edit
            </Button>
            <Button variant="outline" size="sm" onClick={handleReload}>
              <RefreshCwIcon className="mr-2 h-4 w-4" />
              Reload
            </Button>
            <Button variant="destructive" size="sm" onClick={handleDelete}>
              <TrashIcon className="mr-2 h-4 w-4" />
              Delete
            </Button>
          </div>
        </div>
        
        <Tabs defaultValue="overview" className="w-full" onValueChange={setActiveTab}>
          <TabsList>
            <TabsTrigger value="overview">Overview</TabsTrigger>
            <TabsTrigger value="configuration">Configuration</TabsTrigger>
            <TabsTrigger value="dependencies">Dependencies</TabsTrigger>
            <TabsTrigger value="metrics">Metrics</TabsTrigger>
          </TabsList>
          
          <TabsContent value="overview" className="mt-4">
            <Card>
              <CardHeader>
                <CardTitle>Plugin Overview</CardTitle>
                <CardDescription>
                  Basic information about the plugin
                </CardDescription>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <h3 className="text-sm font-medium text-muted-foreground">ID</h3>
                    <p className="mt-1">{plugin.id}</p>
                  </div>
                  <div>
                    <h3 className="text-sm font-medium text-muted-foreground">Type</h3>
                    <p className="mt-1">{getPluginTypeDisplay(plugin.type)}</p>
                  </div>
                  <div>
                    <h3 className="text-sm font-medium text-muted-foreground">Version</h3>
                    <p className="mt-1">{plugin.version || '1.0.0'}</p>
                  </div>
                  <div>
                    <h3 className="text-sm font-medium text-muted-foreground">Status</h3>
                    <p className="mt-1">{plugin.status}</p>
                  </div>
                </div>
              </CardContent>
            </Card>
          </TabsContent>
          
          <TabsContent value="configuration" className="mt-4">
            <Card>
              <CardHeader>
                <CardTitle>Plugin Configuration</CardTitle>
                <CardDescription>
                  Configuration settings for this plugin
                </CardDescription>
              </CardHeader>
              <CardContent>
                <pre className="bg-muted p-4 rounded-md overflow-auto max-h-[500px]">
                  <code>{formatJson(plugin.config)}</code>
                </pre>
              </CardContent>
            </Card>
          </TabsContent>
          
          <TabsContent value="dependencies" className="mt-4">
            <Card>
              <CardHeader>
                <CardTitle>Plugin Dependencies</CardTitle>
                <CardDescription>
                  Other plugins this plugin depends on
                </CardDescription>
              </CardHeader>
              <CardContent>
                {plugin.config?.dependencies?.length > 0 ? (
                  <ul className="list-disc pl-5">
                    {plugin.config.dependencies.map((dep: string) => (
                      <li key={dep} className="mb-2">
                        <a 
                          href={`/dashboard/plugins/${dep}`}
                          className="text-primary hover:underline"
                        >
                          {dep}
                        </a>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="text-muted-foreground">No dependencies</p>
                )}
              </CardContent>
            </Card>
          </TabsContent>
          
          <TabsContent value="metrics" className="mt-4">
            <Card>
              <CardHeader>
                <CardTitle>Plugin Metrics</CardTitle>
                <CardDescription>
                  Performance and usage metrics for this plugin
                </CardDescription>
              </CardHeader>
              <CardContent>
                <p className="text-muted-foreground">Metrics not available</p>
              </CardContent>
            </Card>
          </TabsContent>
        </Tabs>
      </div>
    </DashboardLayout>
  )
}
