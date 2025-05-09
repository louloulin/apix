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
import { PlusIcon, PencilIcon, TrashIcon, RefreshCwIcon, AlertCircle, Activity } from "lucide-react"
import { useToast } from "@/components/ui/use-toast"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { servicesApi, Service, ServiceQueryParams } from "@/lib/api-client/services"

interface ServicesListProps {
  services: Service[]
  onToggleStatus: (service: Service) => void
  onDelete: (service: Service) => void
  onEdit: (service: Service) => void
  onCheckHealth: (service: Service) => void
  isLoading: boolean
}

export default function ServicesPage() {
  const router = useRouter()
  const locale = useLocale()
  const { toast } = useToast()
  const t = useTranslations('services')
  const common = useTranslations('common')
  
  const [services, setServices] = useState<Service[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)
  const [searchQuery, setSearchQuery] = useState("")
  const [currentPage, setCurrentPage] = useState(1)
  const [totalServices, setTotalServices] = useState(0)
  const [pageSize, setPageSize] = useState(10)
  const [sortBy, setSortBy] = useState("name")
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('asc')
  const [isRefreshing, setIsRefreshing] = useState(false)
  const [statusFilter, setStatusFilter] = useState("all")
  
  // 加载服务数据
  const loadServices = async () => {
    try {
      setIsLoading(true)
      setError(null)
      
      // 构建查询参数
      const params: ServiceQueryParams = {
        page: currentPage,
        pageSize,
        sortBy,
        sortOrder
      }
      
      // 添加搜索查询
      if (searchQuery) {
        params.search = searchQuery
      }
      
      // 添加状态过滤
      if (statusFilter === "enabled") {
        params.enabled = true
      } else if (statusFilter === "disabled") {
        params.enabled = false
      }
      
      // 调用 API
      const response = await servicesApi.getServices(params)
      
      // 更新状态
      setServices(response.services)
      setTotalServices(response.total)
      
    } catch (err) {
      console.error("Failed to load services:", err)
      setError(err instanceof Error ? err : new Error('Failed to load services'))
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
    loadServices()
  }, [currentPage, pageSize, sortBy, sortOrder, statusFilter])
  
  // 搜索时使用防抖
  useEffect(() => {
    const timer = setTimeout(() => {
      loadServices()
    }, 300)
    
    return () => clearTimeout(timer)
  }, [searchQuery])
  
  // 刷新服务数据
  const handleRefresh = () => {
    setIsRefreshing(true)
    loadServices()
  }
  
  // 切换服务状态
  const handleToggleStatus = async (service: Service) => {
    try {
      if (service.enabled) {
        // 禁用服务
        const response = await servicesApi.disableService(service.id)
        if (response.success) {
          toast({
            title: common('success'),
            description: t('disabledSuccess', { name: service.name })
          })
        } else {
          throw new Error(response.message || t('disableError'))
        }
      } else {
        // 启用服务
        const response = await servicesApi.enableService(service.id)
        if (response.success) {
          toast({
            title: common('success'),
            description: t('enabledSuccess', { name: service.name })
          })
        } else {
          throw new Error(response.message || t('enableError'))
        }
      }
      
      // 重新加载服务数据
      loadServices()
    } catch (err) {
      console.error("Failed to toggle service status:", err)
      toast({
        title: common('error'),
        description: err instanceof Error ? err.message : 'Unknown error',
        variant: "destructive"
      })
    }
  }

  // 删除服务
  const handleDelete = async (service: Service) => {
    if (confirm(t('deleteConfirm', { name: service.name }))) {
      try {
        const response = await servicesApi.deleteService(service.id)
        if (response.success) {
          toast({
            title: common('success'),
            description: t('deleteSuccess', { name: service.name })
          })
          
          // 重新加载服务数据
          loadServices()
        } else {
          throw new Error(response.message || t('deleteError'))
        }
      } catch (err) {
        console.error("Failed to delete service:", err)
        toast({
          title: common('error'),
          description: err instanceof Error ? err.message : t('deleteError'),
          variant: "destructive"
        })
      }
    }
  }
  
  // 编辑服务
  const handleEdit = (service: Service) => {
    router.push(`/${locale}/dashboard/services/${service.id}/edit`)
  }
  
  // 检查服务健康状态
  const handleCheckHealth = async (service: Service) => {
    try {
      const health = await servicesApi.getServiceHealth(service.id)
      
      toast({
        title: t('healthStatus'),
        description: `${service.name}: ${health.status === 'UP' ? t('healthUp') : health.status === 'DOWN' ? t('healthDown') : t('healthUnknown')}`,
        variant: health.status === 'UP' ? 'default' : 'destructive'
      })
    } catch (err) {
      console.error("Failed to check service health:", err)
      toast({
        title: common('error'),
        description: err instanceof Error ? err.message : t('healthCheckError'),
        variant: "destructive"
      })
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
          <Button onClick={() => router.push(`/${locale}/dashboard/services/create`)}>
            <PlusIcon className="mr-2 h-4 w-4" />
            {t('addService')}
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
                  data-testid="service-search"
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
                </SelectContent>
              </Select>
            </div>

            <ServicesList
              services={services}
              onToggleStatus={handleToggleStatus}
              onDelete={handleDelete}
              onEdit={handleEdit}
              onCheckHealth={handleCheckHealth}
              isLoading={isLoading}
            />
          </div>
        </CardContent>
      </Card>
    </div>
  )
}

function ServicesList({ services, onToggleStatus, onDelete, onEdit, onCheckHealth, isLoading }: ServicesListProps) {
  const t = useTranslations('services')
  const common = useTranslations('common')
  
  // 获取状态颜色类名
  const getStatusColorClass = (enabled: boolean): string => {
    return enabled 
      ? "text-green-600 dark:text-green-400"
      : "text-gray-600 dark:text-gray-400"
  }

  if (isLoading) {
    return (
      <div className="flex justify-center py-8">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
      </div>
    )
  }

  return (
    <div data-testid="services-table">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t('name')}</TableHead>
            <TableHead>{t('url')}</TableHead>
            <TableHead>{common('status')}</TableHead>
            <TableHead className="text-right">{common('actions')}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {services.length === 0 ? (
            <TableRow>
              <TableCell colSpan={4} className="text-center py-8 text-muted-foreground">
                {t('noServices')} {t('adjustSearch')}
              </TableCell>
            </TableRow>
          ) : (
            services.map(service => (
              <TableRow key={service.id} data-testid="service-row">
                <TableCell className="font-medium">{service.name}</TableCell>
                <TableCell>
                  <code className="text-xs bg-muted px-1 py-0.5 rounded">
                    {service.url}
                  </code>
                </TableCell>
                <TableCell>
                  <div className="flex items-center space-x-2">
                    <Switch
                      checked={service.enabled}
                      onCheckedChange={() => onToggleStatus(service)}
                    />
                    <span className={getStatusColorClass(service.enabled)}>
                      {service.enabled ? common('enabled') : common('disabled')}
                    </span>
                  </div>
                </TableCell>
                <TableCell className="text-right">
                  <div className="flex justify-end space-x-1">
                    <Button variant="ghost" size="icon" onClick={() => onCheckHealth(service)}>
                      <Activity className="h-4 w-4" />
                      <span className="sr-only">{t('checkHealth')}</span>
                    </Button>
                    <Button variant="ghost" size="icon" onClick={() => onEdit(service)}>
                      <PencilIcon className="h-4 w-4" />
                      <span className="sr-only">{common('edit')}</span>
                    </Button>
                    <Button variant="ghost" size="icon" onClick={() => onDelete(service)}>
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
