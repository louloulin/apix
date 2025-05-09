"use client"

import { useState, useEffect } from "react"
import { useRouter } from "next/navigation"
import { useTranslations, useLocale } from 'next-intl'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Switch } from "@/components/ui/switch"
import { useToast } from "@/components/ui/use-toast"
import { ArrowLeftIcon, PencilIcon, TrashIcon, RefreshCwIcon } from "lucide-react"
import { getPlugin, enablePlugin, disablePlugin, deletePlugin, reloadPlugin } from "@/lib/api/plugins"
import { getPluginTypeDisplay, getPluginTypeColor, getStatusColor } from "@/lib/utils/plugins"
import { Plugin } from "@/lib/api/plugins"

export default function PluginDetailPage({ params }: { params: { id: string } }) {
  const router = useRouter()
  const locale = useLocale()
  const { toast } = useToast()
  const t = useTranslations('plugins')
  const common = useTranslations('common')
  
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
            title: common('error'),
            description: response.error,
            variant: "destructive"
          })
        }
      } catch (error) {
        toast({
          title: common('error'),
          description: "Failed to fetch plugin details",
          variant: "destructive"
        })
      } finally {
        setLoading(false)
      }
    }
    
    fetchPlugin()
  }, [params.id, common, toast])

  // Handle plugin status toggle
  const handleStatusToggle = async () => {
    if (!plugin) return
    
    try {
      const response = plugin.status === 'enabled' 
        ? await disablePlugin(plugin.id)
        : await enablePlugin(plugin.id)
      
      if (response.success) {
        toast({
          title: common('success'),
          description: `Plugin ${plugin.id} ${plugin.status === 'enabled' ? 'disabled' : 'enabled'} successfully`
        })
        // Refresh plugin data
        const updatedPlugin = await getPlugin(plugin.id)
        if (updatedPlugin.success) {
          setPlugin(updatedPlugin.data)
        }
      } else {
        toast({
          title: common('error'),
          description: response.error,
          variant: "destructive"
        })
      }
    } catch (error) {
      toast({
        title: common('error'),
        description: "Failed to update plugin status",
        variant: "destructive"
      })
    }
  }

  // Handle plugin deletion
  const handleDelete = async () => {
    if (!plugin) return
    
    if (confirm(`${t('deleteConfirm', { name: plugin.id })}`)) {
      try {
        const response = await deletePlugin(plugin.id)
        if (response.success) {
          toast({
            title: common('success'),
            description: `Plugin ${plugin.id} deleted successfully`
          })
          router.push(`/${locale}/dashboard/plugins`)
        } else {
          toast({
            title: common('error'),
            description: response.error,
            variant: "destructive"
          })
        }
      } catch (error) {
        toast({
          title: common('error'),
          description: "Failed to delete plugin",
          variant: "destructive"
        })
      }
    }
  }

  // Handle plugin reload
  const handleReload = async () => {
    if (!plugin) return
    
    try {
      const response = await reloadPlugin(plugin.id)
      if (response.success) {
        toast({
          title: common('success'),
          description: `Plugin ${plugin.id} reloaded successfully`
        })
        // Refresh plugin data
        const updatedPlugin = await getPlugin(plugin.id)
        if (updatedPlugin.success) {
          setPlugin(updatedPlugin.data)
        }
      } else {
        toast({
          title: common('error'),
          description: response.error,
          variant: "destructive"
        })
      }
    } catch (error) {
      toast({
        title: common('error'),
        description: "Failed to reload plugin",
        variant: "destructive"
      })
    }
  }

  if (loading) {
    return (
      <div className="flex justify-center items-center h-[calc(100vh-200px)]">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary"></div>
      </div>
    )
  }

  if (!plugin) {
    return (
      <div className="flex flex-col items-center justify-center h-[calc(100vh-200px)]">
        <h2 className="text-2xl font-bold mb-2">{t('notFound')}</h2>
        <p className="text-muted-foreground mb-4">{t('notFoundDescription')}</p>
        <Button onClick={() => router.push(`/${locale}/dashboard/plugins`)}>
          <ArrowLeftIcon className="mr-2 h-4 w-4" />
          {t('backToPlugins')}
        </Button>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={() => router.push(`/${locale}/dashboard/plugins`)}>
            <ArrowLeftIcon className="mr-2 h-4 w-4" />
            {common('back')}
          </Button>
          <h1 className="text-3xl font-bold">{plugin.id}</h1>
          <Badge variant="outline" className={`bg-${getPluginTypeColor(plugin.type)}-100 text-${getPluginTypeColor(plugin.type)}-800 dark:bg-${getPluginTypeColor(plugin.type)}-900 dark:text-${getPluginTypeColor(plugin.type)}-300 border-${getPluginTypeColor(plugin.type)}-200 ml-2`}>
            {getPluginTypeDisplay(plugin.type)}
          </Badge>
        </div>
        <div className="flex items-center gap-2">
          <div className="flex items-center mr-4">
            <Switch
              checked={plugin.status === 'enabled'}
              onCheckedChange={handleStatusToggle}
              className="mr-2"
            />
            <span className={`text-${getStatusColor(plugin.status)}-600 dark:text-${getStatusColor(plugin.status)}-400`}>
              {plugin.status === 'enabled' ? common('enabled') : common('disabled')}
            </span>
          </div>
          <Button variant="outline" size="icon" onClick={handleReload}>
            <RefreshCwIcon className="h-4 w-4" />
          </Button>
          <Button variant="outline" size="icon" onClick={() => router.push(`/${locale}/dashboard/plugins/${plugin.id}/edit`)}>
            <PencilIcon className="h-4 w-4" />
          </Button>
          <Button variant="outline" size="icon" onClick={handleDelete}>
            <TrashIcon className="h-4 w-4" />
          </Button>
        </div>
      </div>

      <Tabs defaultValue="overview" value={activeTab} onValueChange={setActiveTab}>
        <TabsList>
          <TabsTrigger value="overview">{common('overview')}</TabsTrigger>
          <TabsTrigger value="configuration">{common('configuration')}</TabsTrigger>
          <TabsTrigger value="metrics">{common('metrics')}</TabsTrigger>
        </TabsList>
        
        <TabsContent value="overview" className="space-y-4 mt-4">
          <Card>
            <CardHeader>
              <CardTitle>{t('pluginInfo')}</CardTitle>
            </CardHeader>
            <CardContent>
              <dl className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <dt className="text-sm font-medium text-muted-foreground">ID</dt>
                  <dd className="text-lg">{plugin.id}</dd>
                </div>
                <div>
                  <dt className="text-sm font-medium text-muted-foreground">{common('type')}</dt>
                  <dd className="text-lg">
                    <Badge variant="outline" className={`bg-${getPluginTypeColor(plugin.type)}-100 text-${getPluginTypeColor(plugin.type)}-800 dark:bg-${getPluginTypeColor(plugin.type)}-900 dark:text-${getPluginTypeColor(plugin.type)}-300 border-${getPluginTypeColor(plugin.type)}-200`}>
                      {getPluginTypeDisplay(plugin.type)}
                    </Badge>
                  </dd>
                </div>
                <div>
                  <dt className="text-sm font-medium text-muted-foreground">{t('version')}</dt>
                  <dd className="text-lg">{plugin.version || 'N/A'}</dd>
                </div>
                <div>
                  <dt className="text-sm font-medium text-muted-foreground">{common('status')}</dt>
                  <dd className="text-lg">
                    <span className={`text-${getStatusColor(plugin.status)}-600 dark:text-${getStatusColor(plugin.status)}-400`}>
                      {plugin.status === 'enabled' ? common('enabled') : common('disabled')}
                    </span>
                  </dd>
                </div>
              </dl>
            </CardContent>
          </Card>
        </TabsContent>
        
        <TabsContent value="configuration" className="space-y-4 mt-4">
          <Card>
            <CardHeader>
              <CardTitle>{common('configuration')}</CardTitle>
            </CardHeader>
            <CardContent>
              <pre className="bg-muted p-4 rounded-md overflow-auto max-h-[400px]">
                {JSON.stringify(plugin.config, null, 2)}
              </pre>
            </CardContent>
          </Card>
        </TabsContent>
        
        <TabsContent value="metrics" className="space-y-4 mt-4">
          <Card>
            <CardHeader>
              <CardTitle>{common('metrics')}</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-center py-8 text-muted-foreground">
                {common('noData')}
              </div>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  )
}
