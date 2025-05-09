"use client"

import { useState, useEffect } from "react"
import { useRouter } from "next/navigation"
import { useTranslations, useLocale } from 'next-intl'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Switch } from "@/components/ui/switch"
import { Badge } from "@/components/ui/badge"
import { PlusIcon, PencilIcon, TrashIcon, RefreshCwIcon, AlertCircle } from "lucide-react"
import { useToast } from "@/components/ui/use-toast"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { pluginApi, Plugin, PluginStatus, PluginQueryParams } from "@/lib/api-client/plugins"

interface PluginsListProps {
  plugins: Plugin[]
  onToggleStatus: (plugin: Plugin) => void
  onDelete: (plugin: Plugin) => void
  onReload: (plugin: Plugin) => void
  onEdit: (plugin: Plugin) => void
  isLoading: boolean
}

export default function PluginsPage() {
  const router = useRouter()
  const locale = useLocale()
  const { toast } = useToast()
  const t = useTranslations('plugins')
  const common = useTranslations('common')

  const [plugins, setPlugins] = useState<Plugin[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)
  const [activeTab, setActiveTab] = useState("all")
  const [searchQuery, setSearchQuery] = useState("")
  const [currentPage, setCurrentPage] = useState(1)
  const [totalPlugins, setTotalPlugins] = useState(0)
  const [pageSize, setPageSize] = useState(10)
  const [sortBy, setSortBy] = useState("id")
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('asc')
  const [isRefreshing, setIsRefreshing] = useState(false)
  const [statusFilter, setStatusFilter] = useState("all")

  // 加载插件数据
  const loadPlugins = async () => {
    try {
      setIsLoading(true)
      setError(null)

      // 构建查询参数
      const params: PluginQueryParams = {
        page: currentPage,
        pageSize,
        sortBy,
        sortOrder
      }

      // 添加搜索查询
      if (searchQuery) {
        params.search = searchQuery
      }

      // 添加类型过滤
      if (activeTab !== "all") {
        params.type = activeTab
      }

      // 添加状态过滤
      if (statusFilter === "enabled") {
        params.status = 'enabled'
      } else if (statusFilter === "disabled") {
        params.status = 'disabled'
      } else if (statusFilter === "error") {
        params.status = 'error'
      }

      // 调用 API
      const response = await pluginApi.getPlugins(params)

      // 更新状态
      setPlugins(response.plugins)
      setTotalPlugins(response.total)

    } catch (err) {
      console.error("Failed to load plugins:", err)
      setError(err instanceof Error ? err : new Error('Failed to load plugins'))
      toast({
        title: common('error'),
        description: err instanceof Error ? err.message : 'Unknown error',
        variant: "destructive"
      })
    } finally {
      setIsLoading(false)
      setIsRefreshing(false)
    }
  }

  // 初始加载和参数变化时重新加载
  useEffect(() => {
    loadPlugins()
  }, [currentPage, pageSize, sortBy, sortOrder, activeTab, statusFilter])

  // 搜索时使用防抖
  useEffect(() => {
    const timer = setTimeout(() => {
      loadPlugins()
    }, 300)

    return () => clearTimeout(timer)
  }, [searchQuery])

  // 刷新插件数据
  const handleRefresh = () => {
    setIsRefreshing(true)
    loadPlugins()
  }

  // 切换插件状态
  const handleStatusToggle = async (plugin: Plugin) => {
    try {
      if (plugin.status === 'enabled') {
        // 禁用插件
        const response = await pluginApi.disablePlugin(plugin.id)
        if (response.success) {
          toast({
            title: common('success'),
            description: t('disabledSuccess', { name: plugin.id })
          })
        } else {
          throw new Error(response.message || t('disableError'))
        }
      } else {
        // 启用插件
        const response = await pluginApi.enablePlugin(plugin.id)
        if (response.success) {
          toast({
            title: common('success'),
            description: t('enabledSuccess', { name: plugin.id })
          })
        } else {
          throw new Error(response.message || t('enableError'))
        }
      }

      // 重新加载插件数据
      loadPlugins()
    } catch (err) {
      console.error("Failed to toggle plugin status:", err)
      toast({
        title: common('error'),
        description: err instanceof Error ? err.message : 'Unknown error',
        variant: "destructive"
      })
    }
  }

  // 删除插件
  const handleDelete = async (plugin: Plugin) => {
    if (confirm(t('deleteConfirm', { name: plugin.id }))) {
      try {
        const response = await pluginApi.deletePlugin(plugin.id)
        if (response.success) {
          toast({
            title: common('success'),
            description: t('deleteSuccess', { name: plugin.id })
          })

          // 重新加载插件数据
          loadPlugins()
        } else {
          throw new Error(response.message || t('deleteError'))
        }
      } catch (err) {
        console.error("Failed to delete plugin:", err)
        toast({
          title: common('error'),
          description: err instanceof Error ? err.message : t('deleteError'),
          variant: "destructive"
        })
      }
    }
  }

  // 重新加载插件
  const handleReload = async (plugin: Plugin) => {
    try {
      const response = await pluginApi.reloadPlugin(plugin.id)
      if (response.success) {
        toast({
          title: common('success'),
          description: `${plugin.id} ${t('reloadSuccess')}`
        })

        // 重新加载插件数据
        loadPlugins()
      } else {
        throw new Error(response.message || t('reloadError'))
      }
    } catch (err) {
      console.error("Failed to reload plugin:", err)
      toast({
        title: common('error'),
        description: err instanceof Error ? err.message : t('reloadError'),
        variant: "destructive"
      })
    }
  }

  // 编辑插件
  const handleEdit = (plugin: Plugin) => {
    router.push(`/${locale}/dashboard/plugins/${plugin.id}/edit`)
  }

  // 获取插件类型显示名称
  const getPluginTypeDisplay = (type: string): string => {
    switch (type) {
      case 'authentication':
        return 'Authentication'
      case 'security':
        return 'Security'
      case 'transformation':
        return 'Transformation'
      case 'business-logic':
        return 'Business Logic'
      case 'ai-processing':
        return 'AI Processing'
      case 'caching':
        return 'Caching'
      case 'logging':
        return 'Logging'
      case 'monitoring':
        return 'Monitoring'
      default:
        return type
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
          <Button variant="outline" onClick={handleRefresh} disabled={isRefreshing}>
            <RefreshCwIcon className={`mr-2 h-4 w-4 ${isRefreshing ? 'animate-spin' : ''}`} />
            {common('refresh')}
          </Button>
          <Button onClick={() => router.push(`/${locale}/dashboard/plugins/create`)}>
            <PlusIcon className="mr-2 h-4 w-4" />
            {t('addPlugin')}
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
                  data-testid="plugin-search"
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
              <Select value={statusFilter} onValueChange={setStatusFilter}>
                <SelectTrigger className="w-[180px]">
                  <SelectValue placeholder={common('status')} />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">{t('allStatuses')}</SelectItem>
                  <SelectItem value="enabled">{common('enabled')}</SelectItem>
                  <SelectItem value="disabled">{common('disabled')}</SelectItem>
                  <SelectItem value="error">{common('error')}</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <Tabs value={activeTab} className="w-full" onValueChange={setActiveTab}>
              <TabsList className="grid grid-cols-5 mb-4">
                <TabsTrigger value="all">{t('all')}</TabsTrigger>
                <TabsTrigger value="authentication">{getPluginTypeDisplay('authentication')}</TabsTrigger>
                <TabsTrigger value="security">{getPluginTypeDisplay('security')}</TabsTrigger>
                <TabsTrigger value="transformation">{getPluginTypeDisplay('transformation')}</TabsTrigger>
                <TabsTrigger value="business-logic">{getPluginTypeDisplay('business-logic')}</TabsTrigger>
              </TabsList>

              <TabsContent value={activeTab} className="mt-4">
                <PluginsList
                  plugins={plugins}
                  onToggleStatus={handleStatusToggle}
                  onDelete={handleDelete}
                  onReload={handleReload}
                  onEdit={handleEdit}
                  isLoading={isLoading}
                />
              </TabsContent>
            </Tabs>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}

function PluginsList({ plugins, onToggleStatus, onDelete, onReload, onEdit, isLoading }: PluginsListProps) {
  const t = useTranslations('plugins')
  const common = useTranslations('common')

  // 获取插件类型显示名称
  const getPluginTypeDisplay = (type: string): string => {
    switch (type) {
      case 'authentication':
        return 'Authentication'
      case 'security':
        return 'Security'
      case 'transformation':
        return 'Transformation'
      case 'business-logic':
        return 'Business Logic'
      case 'ai-processing':
        return 'AI Processing'
      case 'caching':
        return 'Caching'
      case 'logging':
        return 'Logging'
      case 'monitoring':
        return 'Monitoring'
      default:
        return type
    }
  }

  // 获取状态颜色类名
  const getStatusColorClass = (status: PluginStatus): string => {
    switch (status) {
      case 'enabled':
        return 'text-green-600 dark:text-green-400'
      case 'disabled':
        return 'text-gray-600 dark:text-gray-400'
      case 'error':
        return 'text-red-600 dark:text-red-400'
      default:
        return 'text-gray-600 dark:text-gray-400'
    }
  }

  if (isLoading) {
    return (
      <div className="flex justify-center py-8">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
      </div>
    )
  }

  return (
    <div data-testid="plugins-table">
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
          {plugins.length === 0 ? (
            <TableRow>
              <TableCell colSpan={5} className="text-center py-8 text-muted-foreground">
                {t('noPlugins')} {t('adjustSearch')}
              </TableCell>
            </TableRow>
          ) : (
            plugins.map(plugin => (
              <TableRow key={plugin.id} data-testid="plugin-row">
                <TableCell className="font-medium">{plugin.id}</TableCell>
                <TableCell>
                  <Badge variant="outline">
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
                    <span className={getStatusColorClass(plugin.status)}>
                      {plugin.status === 'enabled' ? common('enabled') :
                       plugin.status === 'disabled' ? common('disabled') : common('error')}
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
            ))
          )}
        </TableBody>
      </Table>
    </div>
  )
}
