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
import { PlusIcon, TrashIcon, RefreshCwIcon, AlertCircle, Copy, Check } from "lucide-react"
import { useToast } from "@/components/ui/use-toast"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog"
import { Label } from "@/components/ui/label"
import { Checkbox } from "@/components/ui/checkbox"
import { authApi, ApiKey, ApiKeyQueryParams } from "@/lib/api-client/auth"

interface ApiKeysListProps {
  keys: ApiKey[]
  onDelete: (key: ApiKey) => void
  onCopy: (key: ApiKey) => void
  isLoading: boolean
}

export default function ApiKeysPage() {
  const router = useRouter()
  const locale = useLocale()
  const { toast } = useToast()
  const t = useTranslations('apiKeys')
  const common = useTranslations('common')
  
  const [keys, setKeys] = useState<ApiKey[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)
  const [searchQuery, setSearchQuery] = useState("")
  const [currentPage, setCurrentPage] = useState(1)
  const [totalKeys, setTotalKeys] = useState(0)
  const [pageSize, setPageSize] = useState(10)
  const [sortBy, setSortBy] = useState("createdAt")
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('desc')
  const [isRefreshing, setIsRefreshing] = useState(false)
  const [scopeFilter, setScopeFilter] = useState("all")
  const [availableScopes, setAvailableScopes] = useState<Array<{ id: string; name: string; description?: string }>>([])
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false)
  const [newKeyName, setNewKeyName] = useState("")
  const [newKeyDescription, setNewKeyDescription] = useState("")
  const [newKeyScopes, setNewKeyScopes] = useState<string[]>([])
  const [newKeyExpiresAt, setNewKeyExpiresAt] = useState("")
  const [createdKey, setCreatedKey] = useState<ApiKey | null>(null)
  const [isCopied, setIsCopied] = useState(false)
  
  // 加载 API 密钥数据
  const loadKeys = async () => {
    try {
      setIsLoading(true)
      setError(null)
      
      // 构建查询参数
      const params: ApiKeyQueryParams = {
        page: currentPage,
        pageSize,
        sortBy,
        sortOrder
      }
      
      // 添加搜索查询
      if (searchQuery) {
        params.search = searchQuery
      }
      
      // 添加范围过滤
      if (scopeFilter !== "all") {
        params.scope = scopeFilter
      }
      
      // 调用 API
      const [keysResponse, scopesResponse] = await Promise.all([
        authApi.getApiKeys(params),
        authApi.getApiKeyScopes()
      ])
      
      // 更新状态
      setKeys(keysResponse.keys)
      setTotalKeys(keysResponse.total)
      setAvailableScopes(scopesResponse.scopes)
      
    } catch (err) {
      console.error("Failed to load API keys:", err)
      setError(err instanceof Error ? err : new Error('Failed to load API keys'))
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
    loadKeys()
  }, [currentPage, pageSize, sortBy, sortOrder, scopeFilter])
  
  // 搜索时使用防抖
  useEffect(() => {
    const timer = setTimeout(() => {
      loadKeys()
    }, 300)
    
    return () => clearTimeout(timer)
  }, [searchQuery])
  
  // 刷新密钥数据
  const handleRefresh = () => {
    setIsRefreshing(true)
    loadKeys()
  }
  
  // 删除密钥
  const handleDelete = async (key: ApiKey) => {
    if (confirm(t('deleteConfirm', { name: key.name }))) {
      try {
        const response = await authApi.deleteApiKey(key.id)
        if (response.success) {
          toast({
            title: common('success'),
            description: t('deleteSuccess', { name: key.name })
          })
          
          // 重新加载密钥数据
          loadKeys()
        } else {
          throw new Error(response.message || t('deleteError'))
        }
      } catch (err) {
        console.error("Failed to delete API key:", err)
        toast({
          title: common('error'),
          description: err instanceof Error ? err.message : t('deleteError'),
          variant: "destructive"
        })
      }
    }
  }
  
  // 复制密钥
  const handleCopy = (key: ApiKey) => {
    navigator.clipboard.writeText(key.key)
      .then(() => {
        toast({
          title: common('success'),
          description: t('keyCopied')
        })
      })
      .catch((err) => {
        console.error("Failed to copy API key:", err)
        toast({
          title: common('error'),
          description: t('copyError'),
          variant: "destructive"
        })
      })
  }
  
  // 创建新密钥
  const handleCreateKey = async () => {
    try {
      if (!newKeyName) {
        throw new Error(t('nameRequired'))
      }
      
      if (newKeyScopes.length === 0) {
        throw new Error(t('scopesRequired'))
      }
      
      const response = await authApi.createApiKey(
        newKeyName,
        newKeyScopes,
        newKeyExpiresAt || undefined,
        newKeyDescription || undefined
      )
      
      if (response.success) {
        setCreatedKey(response.key)
        toast({
          title: common('success'),
          description: t('createSuccess')
        })
      } else {
        throw new Error(response.message || t('createError'))
      }
    } catch (err) {
      console.error("Failed to create API key:", err)
      toast({
        title: common('error'),
        description: err instanceof Error ? err.message : t('createError'),
        variant: "destructive"
      })
    }
  }
  
  // 复制新创建的密钥
  const handleCopyNewKey = () => {
    if (createdKey) {
      navigator.clipboard.writeText(createdKey.key)
        .then(() => {
          setIsCopied(true)
          setTimeout(() => setIsCopied(false), 2000)
          toast({
            title: common('success'),
            description: t('keyCopied')
          })
        })
        .catch((err) => {
          console.error("Failed to copy API key:", err)
          toast({
            title: common('error'),
            description: t('copyError'),
            variant: "destructive"
          })
        })
    }
  }
  
  // 关闭创建对话框
  const handleCloseCreateDialog = () => {
    setIsCreateDialogOpen(false)
    setNewKeyName("")
    setNewKeyDescription("")
    setNewKeyScopes([])
    setNewKeyExpiresAt("")
    setCreatedKey(null)
    setIsCopied(false)
    
    // 重新加载密钥数据
    loadKeys()
  }
  
  // 处理范围选择
  const handleScopeChange = (scopeId: string, checked: boolean) => {
    if (checked) {
      setNewKeyScopes([...newKeyScopes, scopeId])
    } else {
      setNewKeyScopes(newKeyScopes.filter(id => id !== scopeId))
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
          <Dialog open={isCreateDialogOpen} onOpenChange={setIsCreateDialogOpen}>
            <DialogTrigger asChild>
              <Button>
                <PlusIcon className="mr-2 h-4 w-4" />
                {t('createKey')}
              </Button>
            </DialogTrigger>
            <DialogContent className="sm:max-w-[500px]">
              {!createdKey ? (
                <>
                  <DialogHeader>
                    <DialogTitle>{t('createKey')}</DialogTitle>
                    <DialogDescription>
                      {t('createKeyDescription')}
                    </DialogDescription>
                  </DialogHeader>
                  <div className="grid gap-4 py-4">
                    <div className="grid grid-cols-4 items-center gap-4">
                      <Label htmlFor="name" className="text-right">
                        {t('name')}
                      </Label>
                      <Input
                        id="name"
                        value={newKeyName}
                        onChange={(e) => setNewKeyName(e.target.value)}
                        className="col-span-3"
                        data-testid="key-name-input"
                      />
                    </div>
                    <div className="grid grid-cols-4 items-center gap-4">
                      <Label htmlFor="description" className="text-right">
                        {t('description')}
                      </Label>
                      <Input
                        id="description"
                        value={newKeyDescription}
                        onChange={(e) => setNewKeyDescription(e.target.value)}
                        className="col-span-3"
                      />
                    </div>
                    <div className="grid grid-cols-4 items-center gap-4">
                      <Label htmlFor="expiresAt" className="text-right">
                        {t('expiresAt')}
                      </Label>
                      <Input
                        id="expiresAt"
                        type="date"
                        value={newKeyExpiresAt}
                        onChange={(e) => setNewKeyExpiresAt(e.target.value)}
                        className="col-span-3"
                      />
                    </div>
                    <div className="grid grid-cols-4 items-start gap-4">
                      <Label className="text-right pt-2">
                        {t('scopes')}
                      </Label>
                      <div className="col-span-3 space-y-2">
                        {availableScopes.map((scope) => (
                          <div key={scope.id} className="flex items-center space-x-2">
                            <Checkbox
                              id={`scope-${scope.id}`}
                              checked={newKeyScopes.includes(scope.id)}
                              onCheckedChange={(checked) => handleScopeChange(scope.id, checked === true)}
                            />
                            <Label
                              htmlFor={`scope-${scope.id}`}
                              className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70"
                            >
                              {scope.name}
                              {scope.description && (
                                <span className="text-xs text-muted-foreground ml-2">
                                  {scope.description}
                                </span>
                              )}
                            </Label>
                          </div>
                        ))}
                      </div>
                    </div>
                  </div>
                  <DialogFooter>
                    <Button type="submit" onClick={handleCreateKey} data-testid="create-key-button">
                      {t('createKey')}
                    </Button>
                  </DialogFooter>
                </>
              ) : (
                <>
                  <DialogHeader>
                    <DialogTitle>{t('keyCreated')}</DialogTitle>
                    <DialogDescription>
                      {t('keyCreatedDescription')}
                    </DialogDescription>
                  </DialogHeader>
                  <div className="grid gap-4 py-4">
                    <div className="grid grid-cols-4 items-center gap-4">
                      <Label className="text-right">
                        {t('name')}
                      </Label>
                      <div className="col-span-3 font-medium">
                        {createdKey.name}
                      </div>
                    </div>
                    <div className="grid grid-cols-4 items-center gap-4">
                      <Label className="text-right">
                        {t('key')}
                      </Label>
                      <div className="col-span-3 relative">
                        <Input
                          value={createdKey.key}
                          readOnly
                          className="pr-10 font-mono text-sm"
                          data-testid="created-key-value"
                        />
                        <Button
                          variant="ghost"
                          size="icon"
                          className="absolute right-0 top-0 h-full"
                          onClick={handleCopyNewKey}
                          title={t('copyKey')}
                        >
                          {isCopied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
                        </Button>
                      </div>
                    </div>
                    <div className="grid grid-cols-4 items-center gap-4">
                      <Label className="text-right">
                        {t('scopes')}
                      </Label>
                      <div className="col-span-3 flex flex-wrap gap-1">
                        {createdKey.scopes.map((scope) => (
                          <Badge key={scope} variant="secondary">
                            {scope}
                          </Badge>
                        ))}
                      </div>
                    </div>
                    <Alert>
                      <AlertCircle className="h-4 w-4" />
                      <AlertTitle>{t('important')}</AlertTitle>
                      <AlertDescription>
                        {t('keyWarning')}
                      </AlertDescription>
                    </Alert>
                  </div>
                  <DialogFooter>
                    <Button type="button" onClick={handleCloseCreateDialog}>
                      {common('close')}
                    </Button>
                  </DialogFooter>
                </>
              )}
            </DialogContent>
          </Dialog>
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
                  data-testid="key-search"
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
              
              {availableScopes.length > 0 && (
                <Select value={scopeFilter} onValueChange={setScopeFilter}>
                  <SelectTrigger className="w-[180px]">
                    <SelectValue placeholder={t('scope')} />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">{t('allScopes')}</SelectItem>
                    {availableScopes.map(scope => (
                      <SelectItem key={scope.id} value={scope.id}>{scope.name}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            </div>

            <ApiKeysList
              keys={keys}
              onDelete={handleDelete}
              onCopy={handleCopy}
              isLoading={isLoading}
            />
          </div>
        </CardContent>
      </Card>
    </div>
  )
}

function ApiKeysList({ keys, onDelete, onCopy, isLoading }: ApiKeysListProps) {
  const t = useTranslations('apiKeys')
  const common = useTranslations('common')
  
  // 格式化日期
  const formatDate = (dateString?: string): string => {
    if (!dateString) return t('never')
    
    return new Date(dateString).toLocaleString()
  }

  if (isLoading) {
    return (
      <div className="flex justify-center py-8">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
      </div>
    )
  }

  return (
    <div data-testid="api-keys-table">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t('name')}</TableHead>
            <TableHead>{t('scopes')}</TableHead>
            <TableHead>{t('createdAt')}</TableHead>
            <TableHead>{t('expiresAt')}</TableHead>
            <TableHead>{t('lastUsedAt')}</TableHead>
            <TableHead className="text-right">{common('actions')}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {keys.length === 0 ? (
            <TableRow>
              <TableCell colSpan={6} className="text-center py-8 text-muted-foreground">
                {t('noKeys')} {t('adjustSearch')}
              </TableCell>
            </TableRow>
          ) : (
            keys.map(key => (
              <TableRow key={key.id} data-testid="key-row">
                <TableCell className="font-medium">
                  <div>
                    {key.name}
                    {key.description && (
                      <div className="text-xs text-muted-foreground">
                        {key.description}
                      </div>
                    )}
                  </div>
                </TableCell>
                <TableCell>
                  <div className="flex flex-wrap gap-1">
                    {key.scopes.map(scope => (
                      <Badge key={scope} variant="secondary">
                        {scope}
                      </Badge>
                    ))}
                  </div>
                </TableCell>
                <TableCell>{formatDate(key.createdAt)}</TableCell>
                <TableCell>
                  {key.expiresAt ? formatDate(key.expiresAt) : t('never')}
                </TableCell>
                <TableCell>
                  {key.lastUsedAt ? formatDate(key.lastUsedAt) : t('never')}
                </TableCell>
                <TableCell className="text-right">
                  <div className="flex justify-end space-x-1">
                    <Button variant="ghost" size="icon" onClick={() => onCopy(key)} title={t('copyKey')}>
                      <Copy className="h-4 w-4" />
                      <span className="sr-only">{t('copyKey')}</span>
                    </Button>
                    <Button variant="ghost" size="icon" onClick={() => onDelete(key)}>
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
