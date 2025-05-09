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
import { PlusIcon, PencilIcon, TrashIcon, RefreshCwIcon, AlertCircle, ArrowUpDown } from "lucide-react"
import { useToast } from "@/components/ui/use-toast"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { aiModelsApi, AIRoutingRule, AIRoutingRuleQueryParams, AIModel } from "@/lib/api-client/ai-models"

interface AIRoutingRulesListProps {
  rules: AIRoutingRule[]
  models: AIModel[]
  onToggleStatus: (rule: AIRoutingRule) => void
  onDelete: (rule: AIRoutingRule) => void
  onEdit: (rule: AIRoutingRule) => void
  onChangePriority: (rule: AIRoutingRule, direction: 'up' | 'down') => void
  isLoading: boolean
}

export default function AIRoutingPage() {
  const router = useRouter()
  const locale = useLocale()
  const { toast } = useToast()
  const t = useTranslations('aiRouting')
  const common = useTranslations('common')
  
  const [rules, setRules] = useState<AIRoutingRule[]>([])
  const [models, setModels] = useState<AIModel[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)
  const [searchQuery, setSearchQuery] = useState("")
  const [currentPage, setCurrentPage] = useState(1)
  const [totalRules, setTotalRules] = useState(0)
  const [pageSize, setPageSize] = useState(10)
  const [sortBy, setSortBy] = useState("priority")
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('asc')
  const [isRefreshing, setIsRefreshing] = useState(false)
  const [modelFilter, setModelFilter] = useState("all")
  const [statusFilter, setStatusFilter] = useState("all")
  
  // 加载路由规则数据
  const loadRules = async () => {
    try {
      setIsLoading(true)
      setError(null)
      
      // 构建查询参数
      const params: AIRoutingRuleQueryParams = {
        page: currentPage,
        pageSize,
        sortBy,
        sortOrder
      }
      
      // 添加搜索查询
      if (searchQuery) {
        params.search = searchQuery
      }
      
      // 添加模型过滤
      if (modelFilter !== "all") {
        params.targetModel = modelFilter
      }
      
      // 添加状态过滤
      if (statusFilter === "enabled") {
        params.enabled = true
      } else if (statusFilter === "disabled") {
        params.enabled = false
      }
      
      // 调用 API
      const [rulesResponse, modelsResponse] = await Promise.all([
        aiModelsApi.getRoutingRules(params),
        aiModelsApi.getModels({ enabled: true })
      ])
      
      // 更新状态
      setRules(rulesResponse.rules)
      setTotalRules(rulesResponse.total)
      setModels(modelsResponse.models)
      
    } catch (err) {
      console.error("Failed to load routing rules:", err)
      setError(err instanceof Error ? err : new Error('Failed to load routing rules'))
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
    loadRules()
  }, [currentPage, pageSize, sortBy, sortOrder, modelFilter, statusFilter])
  
  // 搜索时使用防抖
  useEffect(() => {
    const timer = setTimeout(() => {
      loadRules()
    }, 300)
    
    return () => clearTimeout(timer)
  }, [searchQuery])
  
  // 刷新规则数据
  const handleRefresh = () => {
    setIsRefreshing(true)
    loadRules()
  }
  
  // 切换规则状态
  const handleToggleStatus = async (rule: AIRoutingRule) => {
    try {
      if (rule.enabled) {
        // 禁用规则
        const response = await aiModelsApi.disableRoutingRule(rule.id)
        if (response.success) {
          toast({
            title: common('success'),
            description: t('disabledSuccess', { name: rule.name })
          })
        } else {
          throw new Error(response.message || t('disableError'))
        }
      } else {
        // 启用规则
        const response = await aiModelsApi.enableRoutingRule(rule.id)
        if (response.success) {
          toast({
            title: common('success'),
            description: t('enabledSuccess', { name: rule.name })
          })
        } else {
          throw new Error(response.message || t('enableError'))
        }
      }
      
      // 重新加载规则数据
      loadRules()
    } catch (err) {
      console.error("Failed to toggle rule status:", err)
      toast({
        title: common('error'),
        description: err instanceof Error ? err.message : 'Unknown error',
        variant: "destructive"
      })
    }
  }

  // 删除规则
  const handleDelete = async (rule: AIRoutingRule) => {
    if (confirm(t('deleteConfirm', { name: rule.name }))) {
      try {
        const response = await aiModelsApi.deleteRoutingRule(rule.id)
        if (response.success) {
          toast({
            title: common('success'),
            description: t('deleteSuccess', { name: rule.name })
          })
          
          // 重新加载规则数据
          loadRules()
        } else {
          throw new Error(response.message || t('deleteError'))
        }
      } catch (err) {
        console.error("Failed to delete rule:", err)
        toast({
          title: common('error'),
          description: err instanceof Error ? err.message : t('deleteError'),
          variant: "destructive"
        })
      }
    }
  }
  
  // 编辑规则
  const handleEdit = (rule: AIRoutingRule) => {
    router.push(`/${locale}/dashboard/ai-routing/${rule.id}/edit`)
  }
  
  // 更改规则优先级
  const handleChangePriority = async (rule: AIRoutingRule, direction: 'up' | 'down') => {
    try {
      // 计算新的优先级
      const newPriority = direction === 'up' 
        ? Math.max(1, rule.priority - 1)
        : rule.priority + 1
      
      // 更新规则
      const response = await aiModelsApi.updateRoutingRule(rule.id, { priority: newPriority })
      
      if (response.success) {
        toast({
          title: common('success'),
          description: t('priorityChanged', { name: rule.name })
        })
        
        // 重新加载规则数据
        loadRules()
      } else {
        throw new Error(response.message || t('priorityChangeError'))
      }
    } catch (err) {
      console.error("Failed to change rule priority:", err)
      toast({
        title: common('error'),
        description: err instanceof Error ? err.message : t('priorityChangeError'),
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
          <Button onClick={() => router.push(`/${locale}/dashboard/ai-routing/create`)}>
            <PlusIcon className="mr-2 h-4 w-4" />
            {t('addRule')}
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
                  data-testid="rule-search"
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
              
              <Select value={modelFilter} onValueChange={setModelFilter}>
                <SelectTrigger className="w-[180px]">
                  <SelectValue placeholder={t('targetModel')} />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">{t('allModels')}</SelectItem>
                  {models.map(model => (
                    <SelectItem key={model.id} value={model.id}>{model.name}</SelectItem>
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

            <AIRoutingRulesList
              rules={rules}
              models={models}
              onToggleStatus={handleToggleStatus}
              onDelete={handleDelete}
              onEdit={handleEdit}
              onChangePriority={handleChangePriority}
              isLoading={isLoading}
            />
          </div>
        </CardContent>
      </Card>
    </div>
  )
}

function AIRoutingRulesList({ rules, models, onToggleStatus, onDelete, onEdit, onChangePriority, isLoading }: AIRoutingRulesListProps) {
  const t = useTranslations('aiRouting')
  const common = useTranslations('common')
  
  // 获取状态颜色类名
  const getStatusColorClass = (enabled: boolean): string => {
    return enabled 
      ? "text-green-600 dark:text-green-400"
      : "text-gray-600 dark:text-gray-400"
  }
  
  // 获取模型名称
  const getModelName = (modelId: string): string => {
    const model = models.find(m => m.id === modelId)
    return model ? model.name : modelId
  }
  
  // 获取条件类型显示名称
  const getConditionTypeDisplay = (type: string): string => {
    switch (type) {
      case 'path':
        return t('conditionTypePath')
      case 'header':
        return t('conditionTypeHeader')
      case 'query':
        return t('conditionTypeQuery')
      case 'content':
        return t('conditionTypeContent')
      default:
        return type
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
    <div data-testid="ai-routing-table">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t('priority')}</TableHead>
            <TableHead>{t('name')}</TableHead>
            <TableHead>{t('conditionType')}</TableHead>
            <TableHead>{t('pattern')}</TableHead>
            <TableHead>{t('targetModel')}</TableHead>
            <TableHead>{common('status')}</TableHead>
            <TableHead className="text-right">{common('actions')}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {rules.length === 0 ? (
            <TableRow>
              <TableCell colSpan={7} className="text-center py-8 text-muted-foreground">
                {t('noRules')} {t('adjustSearch')}
              </TableCell>
            </TableRow>
          ) : (
            rules.map(rule => (
              <TableRow key={rule.id} data-testid="rule-row">
                <TableCell>
                  <div className="flex items-center space-x-2">
                    <span className="font-medium">{rule.priority}</span>
                    <div className="flex flex-col">
                      <Button variant="ghost" size="icon" onClick={() => onChangePriority(rule, 'up')} title={t('movePriorityUp')}>
                        <ArrowUpDown className="h-4 w-4 rotate-180" />
                        <span className="sr-only">{t('movePriorityUp')}</span>
                      </Button>
                      <Button variant="ghost" size="icon" onClick={() => onChangePriority(rule, 'down')} title={t('movePriorityDown')}>
                        <ArrowUpDown className="h-4 w-4" />
                        <span className="sr-only">{t('movePriorityDown')}</span>
                      </Button>
                    </div>
                  </div>
                </TableCell>
                <TableCell className="font-medium">{rule.name}</TableCell>
                <TableCell>
                  <Badge variant="outline">
                    {getConditionTypeDisplay(rule.condition.type)}
                  </Badge>
                </TableCell>
                <TableCell>
                  <code className="text-xs bg-muted px-1 py-0.5 rounded">
                    {rule.condition.pattern}
                  </code>
                </TableCell>
                <TableCell>{getModelName(rule.targetModel)}</TableCell>
                <TableCell>
                  <div className="flex items-center space-x-2">
                    <Switch
                      checked={rule.enabled}
                      onCheckedChange={() => onToggleStatus(rule)}
                    />
                    <span className={getStatusColorClass(rule.enabled)}>
                      {rule.enabled ? common('enabled') : common('disabled')}
                    </span>
                  </div>
                </TableCell>
                <TableCell className="text-right">
                  <div className="flex justify-end space-x-1">
                    <Button variant="ghost" size="icon" onClick={() => onEdit(rule)}>
                      <PencilIcon className="h-4 w-4" />
                      <span className="sr-only">{common('edit')}</span>
                    </Button>
                    <Button variant="ghost" size="icon" onClick={() => onDelete(rule)}>
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
