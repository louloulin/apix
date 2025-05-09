"use client"

import { useState, useEffect } from "react"
import { useRouter } from "next/navigation"
import { useTranslations, useLocale } from 'next-intl'
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
import { useToast } from "@/components/ui/use-toast"
import { getPlugins, enablePlugin, disablePlugin, deletePlugin, reloadPlugin } from "@/lib/api/plugins"
import { getPluginTypeDisplay, getPluginTypeColor, getStatusColor } from "@/lib/utils/plugins"

// Import the Plugin type from our API client
import { Plugin, PluginStatus } from "@/lib/api/plugins"

interface PluginsListProps {
  plugins: Plugin[]
  onToggleStatus: (plugin: Plugin) => void
  onDelete: (plugin: Plugin) => void
  onReload: (plugin: Plugin) => void
  onEdit: (plugin: Plugin) => void
}

export default function PluginsPage() {
  const router = useRouter()
  const locale = useLocale()
  const { toast } = useToast()
  const t = useTranslations('plugins')
  const common = useTranslations('common')
  
  const [plugins, setPlugins] = useState<Plugin[]>([])
  const [loading, setLoading] = useState(true)
  const [activeTab, setActiveTab] = useState("all")
  const [searchQuery, setSearchQuery] = useState("")

  // Fetch plugins on component mount
  useEffect(() => {
    fetchPlugins()
  }, [])

  // Fetch plugins from API
  const fetchPlugins = async () => {
    setLoading(true)
    try {
      const response = await getPlugins()
      if (response.success) {
        setPlugins(response.data)
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
        description: "Failed to fetch plugins",
        variant: "destructive"
      })
    } finally {
      setLoading(false)
    }
  }

  // Handle plugin status toggle
  const handleStatusToggle = async (plugin: Plugin) => {
    try {
      const response = plugin.status === 'enabled' 
        ? await disablePlugin(plugin.id)
        : await enablePlugin(plugin.id)
      
      if (response.success) {
        toast({
          title: common('success'),
          description: `Plugin ${plugin.id} ${plugin.status === 'enabled' ? 'disabled' : 'enabled'} successfully`
        })
        fetchPlugins() // Refresh the list
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
  const handleDelete = async (plugin: Plugin) => {
    if (confirm(`${t('deleteConfirm', { name: plugin.id })}`)) {
      try {
        const response = await deletePlugin(plugin.id)
        if (response.success) {
          toast({
            title: common('success'),
            description: `Plugin ${plugin.id} deleted successfully`
          })
          fetchPlugins() // Refresh the list
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
  const handleReload = async (plugin: Plugin) => {
    try {
      const response = await reloadPlugin(plugin.id)
      if (response.success) {
        toast({
          title: common('success'),
          description: `Plugin ${plugin.id} reloaded successfully`
        })
        fetchPlugins() // Refresh the list
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

  // Filter plugins based on active tab and search query
  const filteredPlugins = plugins.filter(plugin => {
    const matchesTab = activeTab === "all" || plugin.type === activeTab
    const matchesSearch = plugin.id.toLowerCase().includes(searchQuery.toLowerCase())
    return matchesTab && matchesSearch
  })

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">{t('title')}</h1>
          <p className="text-muted-foreground">
            {t('description')}
          </p>
        </div>
        <Button onClick={() => router.push(`/${locale}/dashboard/plugins/create`)}>
          <PlusIcon className="mr-2 h-4 w-4" />
          {t('addPlugin')}
        </Button>
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
                <Input
                  placeholder={`${common('search')}...`}
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="pl-8"
                />
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
                  />
                </svg>
              </div>
              <Button
                variant="outline"
                size="sm"
                onClick={fetchPlugins}
                disabled={loading}
              >
                <RefreshCwIcon className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
                {common('refresh')}
              </Button>
            </div>

            <Tabs defaultValue="all" value={activeTab} onValueChange={setActiveTab}>
              <TabsList className="grid grid-cols-5 mb-4">
                <TabsTrigger value="all">All</TabsTrigger>
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
                    {common('noData')} {searchQuery && t('adjustSearch')}
                  </div>
                ) : (
                  <PluginsList
                    plugins={filteredPlugins}
                    onToggleStatus={handleStatusToggle}
                    onDelete={handleDelete}
                    onReload={handleReload}
                    onEdit={(plugin) => router.push(`/${locale}/dashboard/plugins/${plugin.id}/edit`)}
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
                      onEdit={(plugin) => router.push(`/${locale}/dashboard/plugins/${plugin.id}/edit`)}
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

function PluginsList({ plugins, onToggleStatus, onDelete, onReload, onEdit }: PluginsListProps) {
  const common = useTranslations('common')
  
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>ID</TableHead>
          <TableHead>{common('type')}</TableHead>
          <TableHead>{common('version')}</TableHead>
          <TableHead>{common('status')}</TableHead>
          <TableHead className="text-right">{common('actions')}</TableHead>
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
            <TableCell>{plugin.version || 'N/A'}</TableCell>
            <TableCell>
              <div className="flex items-center space-x-2">
                <Switch
                  checked={plugin.status === 'enabled'}
                  onCheckedChange={() => onToggleStatus(plugin)}
                />
                <span className={`text-${getStatusColor(plugin.status)}-600 dark:text-${getStatusColor(plugin.status)}-400`}>
                  {plugin.status === 'enabled' ? common('enabled') : common('disabled')}
                </span>
              </div>
            </TableCell>
            <TableCell className="text-right">
              <div className="flex justify-end space-x-1">
                <Button variant="ghost" size="icon" onClick={() => onEdit(plugin)}>
                  <PencilIcon className="h-4 w-4" />
                  <span className="sr-only">{common('edit')}</span>
                </Button>
                <Button variant="ghost" size="icon" onClick={() => onReload(plugin)}>
                  <RefreshCwIcon className="h-4 w-4" />
                  <span className="sr-only">{common('refresh')}</span>
                </Button>
                <Button variant="ghost" size="icon" onClick={() => onDelete(plugin)}>
                  <TrashIcon className="h-4 w-4" />
                  <span className="sr-only">{common('delete')}</span>
                </Button>
              </div>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
