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
import { routesApi, Route } from "@/lib/api-client/routes"

interface RoutesListProps {
  routes: Route[]
  onToggleStatus: (route: Route) => void
  onDelete: (route: Route) => void
  onEdit: (route: Route) => void
  isLoading: boolean
}

export default function RoutesPage() {
  const router = useRouter()
  const locale = useLocale()
  const { toast } = useToast()
  const t = useTranslations('routes')
  const common = useTranslations('common')

  const [activeTab, setActiveTab] = useState("all")
  const [searchQuery, setSearchQuery] = useState("")
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)
  const [routes, setRoutes] = useState<Route[]>([])
  const [currentPage, setCurrentPage] = useState(1)
  const [totalRoutes, setTotalRoutes] = useState(0)
  const [pageSize, setPageSize] = useState(10)
  const [sortBy, setSortBy] = useState("path")
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('asc')
  const [isRefreshing, setIsRefreshing] = useState(false)
  const [statusFilter, setStatusFilter] = useState("all")

  // 加载路由数据
  const loadRoutes = async () => {
    try {
      setIsLoading(true)
      setError(null)

      // 构建查询参数
      const params: any = {
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
      if (statusFilter === "active") {
        params.enabled = true
      } else if (statusFilter === "inactive") {
        params.enabled = false
      }

      // 调用 API
      const response = await routesApi.getRoutes(params)

      // 更新状态
      setRoutes(response.routes)
      setTotalRoutes(response.total)

    } catch (err) {
      console.error("Failed to load routes:", err)
      setError(err instanceof Error ? err : new Error('Failed to load routes'))
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
    loadRoutes()
  }, [currentPage, pageSize, sortBy, sortOrder, activeTab, statusFilter])

  // 搜索时使用防抖
  useEffect(() => {
    const timer = setTimeout(() => {
      loadRoutes()
    }, 300)

    return () => clearTimeout(timer)
  }, [searchQuery])

  // 刷新路由数据
  const handleRefresh = () => {
    setIsRefreshing(true)
    loadRoutes()
  }

  // 切换路由状态
  const handleToggleStatus = async (route: Route) => {
    try {
      if (route.enabled) {
        // 禁用路由
        await routesApi.disableRoute(route.id)
        toast({
          title: common('success'),
          description: t('routeDisabled', { path: route.path })
        })
      } else {
        // 启用路由
        await routesApi.enableRoute(route.id)
        toast({
          title: common('success'),
          description: t('routeEnabled', { path: route.path })
        })
      }

      // 重新加载路由数据
      loadRoutes()
    } catch (err) {
      console.error("Failed to toggle route status:", err)
      toast({
        title: common('error'),
        description: err instanceof Error ? err.message : 'Unknown error',
        variant: "destructive"
      })
    }
  }

  // 删除路由
  const handleDelete = async (route: Route) => {
    if (confirm(t('deleteConfirm', { path: route.path }))) {
      try {
        await routesApi.deleteRoute(route.id)
        toast({
          title: common('success'),
          description: t('routeDeleted', { path: route.path })
        })

        // 重新加载路由数据
        loadRoutes()
      } catch (err) {
        console.error("Failed to delete route:", err)
        toast({
          title: common('error'),
          description: err instanceof Error ? err.message : 'Unknown error',
          variant: "destructive"
        })
      }
    }
  }

  // 编辑路由
  const handleEdit = (route: Route) => {
    router.push(`/${locale}/dashboard/routes/${route.id}/edit`)
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
          <Button onClick={() => router.push(`/${locale}/dashboard/routes/create`)}>
            <PlusIcon className="mr-2 h-4 w-4" />
            {t('addRoute')}
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
                  <SelectItem value="active">{common('active')}</SelectItem>
                  <SelectItem value="inactive">{common('inactive')}</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <Tabs value={activeTab} className="w-full" onValueChange={setActiveTab}>
              <TabsList>
                <TabsTrigger value="all">{t('allRoutes')}</TabsTrigger>
                <TabsTrigger value="llm">{t('llmRoutes')}</TabsTrigger>
                <TabsTrigger value="vector">{t('vectorRoutes')}</TabsTrigger>
                <TabsTrigger value="other">{t('otherRoutes')}</TabsTrigger>
              </TabsList>

              <TabsContent value={activeTab} className="mt-4">
                <RoutesList
                  routes={routes}
                  onToggleStatus={handleToggleStatus}
                  onDelete={handleDelete}
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

function RoutesList({ routes, onToggleStatus, onDelete, onEdit, isLoading }: RoutesListProps) {
  const t = useTranslations('routes')
  const common = useTranslations('common')

  if (isLoading) {
    return (
      <div className="flex justify-center py-8">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
      </div>
    )
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>{t('path')}</TableHead>
          <TableHead>{t('target')}</TableHead>
          <TableHead>{t('methods')}</TableHead>
          <TableHead>{common('status')}</TableHead>
          <TableHead className="text-right">{common('actions')}</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {routes.length === 0 ? (
          <TableRow>
            <TableCell colSpan={5} className="text-center py-8 text-muted-foreground">
              {t('noRoutes')}
            </TableCell>
          </TableRow>
        ) : (
          routes.map(route => (
            <TableRow key={route.id}>
              <TableCell className="font-medium">{route.path}</TableCell>
              <TableCell>{route.targetUrl}</TableCell>
              <TableCell>
                <div className="flex flex-wrap gap-1">
                  {route.methods.map(method => (
                    <Badge key={method} variant="outline" className="bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-300 border-blue-200">
                      {method}
                    </Badge>
                  ))}
                </div>
              </TableCell>
              <TableCell>
                <div className="flex items-center space-x-2">
                  <Switch
                    checked={route.enabled}
                    onCheckedChange={() => onToggleStatus(route)}
                  />
                  <span className={route.enabled ? "text-green-600 dark:text-green-400" : "text-red-600 dark:text-red-400"}>
                    {route.enabled ? common('active') : common('inactive')}
                  </span>
                </div>
              </TableCell>
              <TableCell className="text-right">
                <div className="flex justify-end space-x-1">
                  <Button variant="ghost" size="icon" onClick={() => onEdit(route)}>
                    <PencilIcon className="h-4 w-4" />
                    <span className="sr-only">{common('edit')}</span>
                  </Button>
                  <Button variant="ghost" size="icon" onClick={() => onDelete(route)}>
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
  )
}
