"use client"

import { useState, useEffect } from "react"
import { useRouter } from "next/navigation"
import { useTranslations, useLocale } from 'next-intl'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Switch } from "@/components/ui/switch"
import { Badge } from "@/components/ui/badge"
import { PlusIcon, PencilIcon, TrashIcon, RefreshCwIcon, AlertCircle, Zap } from "lucide-react"
import { useToast } from "@/components/ui/use-toast"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { aiModelsApi, AIModel, AIModelQueryParams } from "@/lib/api-client/ai-models"

interface AIModelsListProps {
  models: AIModel[]
  onToggleStatus: (model: AIModel) => void
  onDelete: (model: AIModel) => void
  onEdit: (model: AIModel) => void
  onTest: (model: AIModel) => void
  isLoading: boolean
}

export default function AIModelsPage() {
  const router = useRouter()
  const locale = useLocale()
  const { toast } = useToast()
  const t = useTranslations('aiModels')
  const common = useTranslations('common')
  
  const [models, setModels] = useState<AIModel[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)
  const [searchQuery, setSearchQuery] = useState("")
  const [currentPage, setCurrentPage] = useState(1)
  const [totalModels, setTotalModels] = useState(0)
  const [pageSize, setPageSize] = useState(10)
  const [sortBy, setSortBy] = useState("name")
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('asc')
  const [isRefreshing, setIsRefreshing] = useState(false)
  const [providerFilter, setProviderFilter] = useState("all")
  const [statusFilter, setStatusFilter] = useState("all")
  const [providers, setProviders] = useState<string[]>([])
  
  // 加载 AI 模型数据
  const loadModels = async () => {
    try {
      setIsLoading(true)
      setError(null)
      
      // 构建查询参数
      const params: AIModelQueryParams = {
        page: currentPage,
        pageSize,
        sortBy,
        sortOrder
      }
      
      // 添加搜索查询
      if (searchQuery) {
        params.search = searchQuery
      }
      
      // 添加提供商过滤
      if (providerFilter !== "all") {
        params.provider = providerFilter
      }
      
      // 添加状态过滤
      if (statusFilter === "enabled") {
        params.enabled = true
      } else if (statusFilter === "disabled") {
        params.enabled = false
      }
      
      // 调用 API
      const response = await aiModelsApi.getModels(params)
      
      // 更新状态
      setModels(response.models)
      setTotalModels(response.total)
      
      // 提取所有提供商
      const uniqueProviders = Array.from(new Set(response.models.map(model => model.provider)))
      setProviders(uniqueProviders)
      
    } catch (err) {
      console.error("Failed to load AI models:", err)
      setError(err instanceof Error ? err : new Error('Failed to load AI models'))
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
    loadModels()
  }, [currentPage, pageSize, sortBy, sortOrder, providerFilter, statusFilter])
  
  // 搜索时使用防抖
  useEffect(() => {
    const timer = setTimeout(() => {
      loadModels()
    }, 300)
    
    return () => clearTimeout(timer)
  }, [searchQuery])
  
  // 刷新模型数据
  const handleRefresh = () => {
    setIsRefreshing(true)
    loadModels()
  }
  
  // 切换模型状态
  const handleToggleStatus = async (model: AIModel) => {
    try {
      if (model.enabled) {
        // 禁用模型
        const response = await aiModelsApi.disableModel(model.id)
        if (response.success) {
          toast({
            title: common('success'),
            description: t('disabledSuccess', { name: model.name })
          })
        } else {
          throw new Error(response.message || t('disableError'))
        }
      } else {
        // 启用模型
        const response = await aiModelsApi.enableModel(model.id)
        if (response.success) {
          toast({
            title: common('success'),
            description: t('enabledSuccess', { name: model.name })
          })
        } else {
          throw new Error(response.message || t('enableError'))
        }
      }
      
      // 重新加载模型数据
      loadModels()
    } catch (err) {
      console.error("Failed to toggle model status:", err)
      toast({
        title: common('error'),
        description: err instanceof Error ? err.message : 'Unknown error',
        variant: "destructive"
      })
    }
  }

  // 删除模型
  const handleDelete = async (model: AIModel) => {
    if (confirm(t('deleteConfirm', { name: model.name }))) {
      try {
        const response = await aiModelsApi.deleteModel(model.id)
        if (response.success) {
          toast({
            title: common('success'),
            description: t('deleteSuccess', { name: model.name })
          })
          
          // 重新加载模型数据
          loadModels()
        } else {
          throw new Error(response.message || t('deleteError'))
        }
      } catch (err) {
        console.error("Failed to delete model:", err)
        toast({
          title: common('error'),
          description: err instanceof Error ? err.message : t('deleteError'),
          variant: "destructive"
        })
      }
    }
  }
  
  // 编辑模型
  const handleEdit = (model: AIModel) => {
    router.push(`/${locale}/dashboard/ai-models/${model.id}/edit`)
  }
  
  // 测试模型连接
  const handleTestConnection = async (model: AIModel) => {
    try {
      const response = await aiModelsApi.testModelConnection(model.id)
      if (response.success) {
        toast({
          title: common('success'),
          description: t('testSuccess', { name: model.name })
        })
      } else {
        throw new Error(response.message || t('testError'))
      }
    } catch (err) {
      console.error("Failed to test model connection:", err)
      toast({
        title: common('error'),
        description: err instanceof Error ? err.message : t('testError'),
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
          <Button onClick={() => router.push(`/${locale}/dashboard/ai-models/create`)}>
            <PlusIcon className="mr-2 h-4 w-4" />
            {t('addModel')}
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
            <div className="flex flex-col sm:flex-row items-start sm:items-center gap-4">
              <div className="relative flex-1 max-w-sm">
                <Input
                  placeholder={`${common('search')}...`}
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="pl-8"
                  data-testid="model-search"
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
              
              <Select value={providerFilter} onValueChange={setProviderFilter}>
                <SelectTrigger className="w-[180px]">
                  <SelectValue placeholder={t('provider')} />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">{t('allProviders')}</SelectItem>
                  {providers.map(provider => (
                    <SelectItem key={provider} value={provider}>{provider}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
              
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

            <AIModelsList
              models={models}
              onToggleStatus={handleToggleStatus}
              onDelete={handleDelete}
              onEdit={handleEdit}
              onTest={handleTestConnection}
              isLoading={isLoading}
            />
          </div>
        </CardContent>
      </Card>
    </div>
  )
}

function AIModelsList({ models, onToggleStatus, onDelete, onEdit, onTest, isLoading }: AIModelsListProps) {
  const t = useTranslations('aiModels')
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
    <div data-testid="ai-models-table">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t('name')}</TableHead>
            <TableHead>{t('provider')}</TableHead>
            <TableHead>{t('maxTokens')}</TableHead>
            <TableHead>{common('status')}</TableHead>
            <TableHead className="text-right">{common('actions')}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {models.length === 0 ? (
            <TableRow>
              <TableCell colSpan={5} className="text-center py-8 text-muted-foreground">
                {t('noModels')} {t('adjustSearch')}
              </TableCell>
            </TableRow>
          ) : (
            models.map(model => (
              <TableRow key={model.id} data-testid="model-row">
                <TableCell className="font-medium">{model.name}</TableCell>
                <TableCell>
                  <Badge variant="outline">
                    {model.provider}
                  </Badge>
                </TableCell>
                <TableCell>{model.maxTokens || t('notSpecified')}</TableCell>
                <TableCell>
                  <div className="flex items-center space-x-2">
                    <Switch
                      checked={model.enabled}
                      onCheckedChange={() => onToggleStatus(model)}
                    />
                    <span className={getStatusColorClass(model.enabled)}>
                      {model.enabled ? common('enabled') : common('disabled')}
                    </span>
                  </div>
                </TableCell>
                <TableCell className="text-right">
                  <div className="flex justify-end space-x-1">
                    <Button variant="ghost" size="icon" onClick={() => onTest(model)} title={t('testConnection')}>
                      <Zap className="h-4 w-4" />
                      <span className="sr-only">{t('testConnection')}</span>
                    </Button>
                    <Button variant="ghost" size="icon" onClick={() => onEdit(model)}>
                      <PencilIcon className="h-4 w-4" />
                      <span className="sr-only">{common('edit')}</span>
                    </Button>
                    <Button variant="ghost" size="icon" onClick={() => onDelete(model)}>
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
