"use client"

import { useState, useEffect } from "react"
import { useRouter } from "next/navigation"
import { useTranslations, useLocale } from 'next-intl'
import { Card, CardContent, CardDescription, CardHeader, CardTitle, CardFooter } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Textarea } from "@/components/ui/textarea"
import { Switch } from "@/components/ui/switch"
import { Label } from "@/components/ui/label"
import { Input } from "@/components/ui/input"
import { AlertCircle, Save, RefreshCw, PowerIcon } from "lucide-react"
import { useToast } from "@/components/ui/use-toast"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { configApi, GatewayConfig } from "@/lib/api-client/config"

export default function SettingsPage() {
  const router = useRouter()
  const locale = useLocale()
  const { toast } = useToast()
  const t = useTranslations('settings')
  const common = useTranslations('common')
  
  const [config, setConfig] = useState<GatewayConfig | null>(null)
  const [configJson, setConfigJson] = useState("")
  const [isLoading, setIsLoading] = useState(true)
  const [isSaving, setIsSaving] = useState(false)
  const [isRestarting, setIsRestarting] = useState(false)
  const [isReloading, setIsReloading] = useState(false)
  const [error, setError] = useState<Error | null>(null)
  const [activeTab, setActiveTab] = useState("general")
  const [jsonError, setJsonError] = useState<string | null>(null)
  
  // 加载配置数据
  const loadConfig = async () => {
    try {
      setIsLoading(true)
      setError(null)
      
      // 调用 API
      const response = await configApi.getConfig()
      
      // 更新状态
      setConfig(response.config)
      setConfigJson(JSON.stringify(response.config, null, 2))
      
    } catch (err) {
      console.error("Failed to load config:", err)
      setError(err instanceof Error ? err : new Error('Failed to load config'))
      toast({
        title: common('error'),
        description: err instanceof Error ? err.message : 'Unknown error',
        variant: "destructive"
      })
    } finally {
      setIsLoading(false)
    }
  }
  
  // 初始加载
  useEffect(() => {
    loadConfig()
  }, [])
  
  // 保存配置
  const handleSaveConfig = async () => {
    try {
      setIsSaving(true)
      setError(null)
      setJsonError(null)
      
      let configData: GatewayConfig
      
      // 解析 JSON
      try {
        configData = JSON.parse(configJson)
      } catch (err) {
        setJsonError(t('invalidJson'))
        throw new Error(t('invalidJson'))
      }
      
      // 调用 API
      const response = await configApi.updateConfig(configData)
      
      if (response.success) {
        toast({
          title: common('success'),
          description: t('configSaved')
        })
        
        // 更新状态
        if (response.config) {
          setConfig(response.config)
          setConfigJson(JSON.stringify(response.config, null, 2))
        }
      } else {
        throw new Error(response.message || t('saveError'))
      }
    } catch (err) {
      console.error("Failed to save config:", err)
      setError(err instanceof Error ? err : new Error('Failed to save config'))
      toast({
        title: common('error'),
        description: err instanceof Error ? err.message : 'Unknown error',
        variant: "destructive"
      })
    } finally {
      setIsSaving(false)
    }
  }
  
  // 重启网关
  const handleRestartGateway = async () => {
    if (confirm(t('restartConfirm'))) {
      try {
        setIsRestarting(true)
        setError(null)
        
        // 调用 API
        const response = await configApi.restartGateway()
        
        if (response.success) {
          toast({
            title: common('success'),
            description: t('restartSuccess')
          })
        } else {
          throw new Error(response.message || t('restartError'))
        }
      } catch (err) {
        console.error("Failed to restart gateway:", err)
        setError(err instanceof Error ? err : new Error('Failed to restart gateway'))
        toast({
          title: common('error'),
          description: err instanceof Error ? err.message : 'Unknown error',
          variant: "destructive"
        })
      } finally {
        setIsRestarting(false)
      }
    }
  }
  
  // 重新加载配置
  const handleReloadConfig = async () => {
    try {
      setIsReloading(true)
      setError(null)
      
      // 调用 API
      const response = await configApi.reloadConfig()
      
      if (response.success) {
        toast({
          title: common('success'),
          description: t('reloadSuccess')
        })
        
        // 重新加载配置
        await loadConfig()
      } else {
        throw new Error(response.message || t('reloadError'))
      }
    } catch (err) {
      console.error("Failed to reload config:", err)
      setError(err instanceof Error ? err : new Error('Failed to reload config'))
      toast({
        title: common('error'),
        description: err instanceof Error ? err.message : 'Unknown error',
        variant: "destructive"
      })
    } finally {
      setIsReloading(false)
    }
  }
  
  // 更新配置字段
  const updateConfig = (path: string[], value: any) => {
    if (!config) return
    
    // 创建配置的深拷贝
    const newConfig = JSON.parse(JSON.stringify(config))
    
    // 递归更新配置
    let current = newConfig
    for (let i = 0; i < path.length - 1; i++) {
      if (!current[path[i]]) {
        current[path[i]] = {}
      }
      current = current[path[i]]
    }
    
    // 设置值
    current[path[path.length - 1]] = value
    
    // 更新状态
    setConfig(newConfig)
    setConfigJson(JSON.stringify(newConfig, null, 2))
  }
  
  // 渲染加载状态
  if (isLoading) {
    return (
      <div className="flex justify-center py-8">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
      </div>
    )
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
          <Button variant="outline" onClick={handleReloadConfig} disabled={isReloading}>
            <RefreshCw className={`mr-2 h-4 w-4 ${isReloading ? 'animate-spin' : ''}`} />
            {t('reloadConfig')}
          </Button>
          <Button variant="outline" onClick={handleRestartGateway} disabled={isRestarting}>
            <PowerIcon className="mr-2 h-4 w-4" />
            {t('restartGateway')}
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
          <Tabs value={activeTab} onValueChange={setActiveTab}>
            <TabsList className="grid grid-cols-5 mb-4">
              <TabsTrigger value="general">{t('general')}</TabsTrigger>
              <TabsTrigger value="admin">{t('admin')}</TabsTrigger>
              <TabsTrigger value="logging">{t('logging')}</TabsTrigger>
              <TabsTrigger value="advanced">{t('advanced')}</TabsTrigger>
              <TabsTrigger value="json">{t('json')}</TabsTrigger>
            </TabsList>
            
            <TabsContent value="general">
              {config && (
                <div className="space-y-4">
                  <div className="grid grid-cols-2 gap-4">
                    <div className="space-y-2">
                      <Label htmlFor="gateway-host">{t('gatewayHost')}</Label>
                      <Input
                        id="gateway-host"
                        value={config.gateway.host}
                        onChange={(e) => updateConfig(['gateway', 'host'], e.target.value)}
                        data-testid="gateway-host"
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="gateway-port">{t('gatewayPort')}</Label>
                      <Input
                        id="gateway-port"
                        type="number"
                        value={config.gateway.port}
                        onChange={(e) => updateConfig(['gateway', 'port'], parseInt(e.target.value))}
                        data-testid="gateway-port"
                      />
                    </div>
                  </div>
                  
                  <div className="space-y-2">
                    <div className="flex items-center space-x-2">
                      <Switch
                        id="ssl-enabled"
                        checked={config.gateway.ssl?.enabled || false}
                        onCheckedChange={(checked) => updateConfig(['gateway', 'ssl', 'enabled'], checked)}
                        data-testid="ssl-enabled"
                      />
                      <Label htmlFor="ssl-enabled">{t('sslEnabled')}</Label>
                    </div>
                  </div>
                  
                  {config.gateway.ssl?.enabled && (
                    <div className="grid grid-cols-2 gap-4 pl-6">
                      <div className="space-y-2">
                        <Label htmlFor="ssl-cert-path">{t('sslCertPath')}</Label>
                        <Input
                          id="ssl-cert-path"
                          value={config.gateway.ssl.certPath || ''}
                          onChange={(e) => updateConfig(['gateway', 'ssl', 'certPath'], e.target.value)}
                        />
                      </div>
                      <div className="space-y-2">
                        <Label htmlFor="ssl-key-path">{t('sslKeyPath')}</Label>
                        <Input
                          id="ssl-key-path"
                          value={config.gateway.ssl.keyPath || ''}
                          onChange={(e) => updateConfig(['gateway', 'ssl', 'keyPath'], e.target.value)}
                        />
                      </div>
                    </div>
                  )}
                  
                  <div className="space-y-2">
                    <div className="flex items-center space-x-2">
                      <Switch
                        id="cors-enabled"
                        checked={config.gateway.cors?.enabled || false}
                        onCheckedChange={(checked) => updateConfig(['gateway', 'cors', 'enabled'], checked)}
                      />
                      <Label htmlFor="cors-enabled">{t('corsEnabled')}</Label>
                    </div>
                  </div>
                  
                  {config.gateway.cors?.enabled && (
                    <div className="space-y-4 pl-6">
                      <div className="space-y-2">
                        <Label htmlFor="cors-allowed-origins">{t('corsAllowedOrigins')}</Label>
                        <Input
                          id="cors-allowed-origins"
                          value={(config.gateway.cors.allowedOrigins || []).join(', ')}
                          onChange={(e) => updateConfig(['gateway', 'cors', 'allowedOrigins'], e.target.value.split(',').map(s => s.trim()))}
                          placeholder="*, http://localhost:3000"
                        />
                      </div>
                      <div className="space-y-2">
                        <Label htmlFor="cors-allowed-methods">{t('corsAllowedMethods')}</Label>
                        <Input
                          id="cors-allowed-methods"
                          value={(config.gateway.cors.allowedMethods || []).join(', ')}
                          onChange={(e) => updateConfig(['gateway', 'cors', 'allowedMethods'], e.target.value.split(',').map(s => s.trim()))}
                          placeholder="GET, POST, PUT, DELETE"
                        />
                      </div>
                    </div>
                  )}
                </div>
              )}
            </TabsContent>
            
            <TabsContent value="admin">
              {config && (
                <div className="space-y-4">
                  <div className="space-y-2">
                    <div className="flex items-center space-x-2">
                      <Switch
                        id="admin-enabled"
                        checked={config.admin.enabled}
                        onCheckedChange={(checked) => updateConfig(['admin', 'enabled'], checked)}
                        data-testid="admin-enabled"
                      />
                      <Label htmlFor="admin-enabled">{t('adminEnabled')}</Label>
                    </div>
                  </div>
                  
                  {config.admin.enabled && (
                    <>
                      <div className="grid grid-cols-2 gap-4">
                        <div className="space-y-2">
                          <Label htmlFor="admin-host">{t('adminHost')}</Label>
                          <Input
                            id="admin-host"
                            value={config.admin.host}
                            onChange={(e) => updateConfig(['admin', 'host'], e.target.value)}
                          />
                        </div>
                        <div className="space-y-2">
                          <Label htmlFor="admin-port">{t('adminPort')}</Label>
                          <Input
                            id="admin-port"
                            type="number"
                            value={config.admin.port}
                            onChange={(e) => updateConfig(['admin', 'port'], parseInt(e.target.value))}
                          />
                        </div>
                      </div>
                      
                      <div className="space-y-2">
                        <div className="flex items-center space-x-2">
                          <Switch
                            id="admin-auth-enabled"
                            checked={config.admin.auth?.enabled || false}
                            onCheckedChange={(checked) => updateConfig(['admin', 'auth', 'enabled'], checked)}
                          />
                          <Label htmlFor="admin-auth-enabled">{t('adminAuthEnabled')}</Label>
                        </div>
                      </div>
                      
                      {config.admin.auth?.enabled && (
                        <div className="space-y-4 pl-6">
                          <div className="space-y-2">
                            <Label htmlFor="admin-auth-type">{t('adminAuthType')}</Label>
                            <Input
                              id="admin-auth-type"
                              value={config.admin.auth.type}
                              onChange={(e) => updateConfig(['admin', 'auth', 'type'], e.target.value)}
                            />
                          </div>
                        </div>
                      )}
                    </>
                  )}
                </div>
              )}
            </TabsContent>
            
            <TabsContent value="logging">
              {config && (
                <div className="space-y-4">
                  <div className="space-y-2">
                    <Label htmlFor="logging-level">{t('loggingLevel')}</Label>
                    <select
                      id="logging-level"
                      className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                      value={config.logging.level}
                      onChange={(e) => updateConfig(['logging', 'level'], e.target.value)}
                      data-testid="logging-level"
                    >
                      <option value="TRACE">TRACE</option>
                      <option value="DEBUG">DEBUG</option>
                      <option value="INFO">INFO</option>
                      <option value="WARN">WARN</option>
                      <option value="ERROR">ERROR</option>
                    </select>
                  </div>
                  
                  <div className="space-y-2">
                    <div className="flex items-center space-x-2">
                      <Switch
                        id="logging-console"
                        checked={config.logging.console}
                        onCheckedChange={(checked) => updateConfig(['logging', 'console'], checked)}
                      />
                      <Label htmlFor="logging-console">{t('loggingConsole')}</Label>
                    </div>
                  </div>
                  
                  <div className="space-y-2">
                    <Label htmlFor="logging-file">{t('loggingFile')}</Label>
                    <Input
                      id="logging-file"
                      value={config.logging.file || ''}
                      onChange={(e) => updateConfig(['logging', 'file'], e.target.value)}
                      placeholder="/var/log/apix.log"
                    />
                  </div>
                </div>
              )}
            </TabsContent>
            
            <TabsContent value="advanced">
              {config && (
                <div className="space-y-4">
                  <div className="space-y-2">
                    <div className="flex items-center space-x-2">
                      <Switch
                        id="metrics-enabled"
                        checked={config.metrics?.enabled || false}
                        onCheckedChange={(checked) => updateConfig(['metrics', 'enabled'], checked)}
                      />
                      <Label htmlFor="metrics-enabled">{t('metricsEnabled')}</Label>
                    </div>
                  </div>
                  
                  {config.metrics?.enabled && (
                    <div className="space-y-2 pl-6">
                      <div className="flex items-center space-x-2">
                        <Switch
                          id="prometheus-enabled"
                          checked={config.metrics.prometheus?.enabled || false}
                          onCheckedChange={(checked) => updateConfig(['metrics', 'prometheus', 'enabled'], checked)}
                        />
                        <Label htmlFor="prometheus-enabled">{t('prometheusEnabled')}</Label>
                      </div>
                      
                      {config.metrics.prometheus?.enabled && (
                        <div className="space-y-2 pl-6">
                          <Label htmlFor="prometheus-path">{t('prometheusPath')}</Label>
                          <Input
                            id="prometheus-path"
                            value={config.metrics.prometheus.path || '/metrics'}
                            onChange={(e) => updateConfig(['metrics', 'prometheus', 'path'], e.target.value)}
                          />
                        </div>
                      )}
                    </div>
                  )}
                  
                  <div className="space-y-2">
                    <div className="flex items-center space-x-2">
                      <Switch
                        id="cluster-enabled"
                        checked={config.cluster?.enabled || false}
                        onCheckedChange={(checked) => updateConfig(['cluster', 'enabled'], checked)}
                      />
                      <Label htmlFor="cluster-enabled">{t('clusterEnabled')}</Label>
                    </div>
                  </div>
                  
                  {config.cluster?.enabled && (
                    <div className="space-y-4 pl-6">
                      <div className="space-y-2">
                        <Label htmlFor="cluster-node-name">{t('clusterNodeName')}</Label>
                        <Input
                          id="cluster-node-name"
                          value={config.cluster.nodeName || ''}
                          onChange={(e) => updateConfig(['cluster', 'nodeName'], e.target.value)}
                        />
                      </div>
                      <div className="space-y-2">
                        <Label htmlFor="cluster-nodes">{t('clusterNodes')}</Label>
                        <Input
                          id="cluster-nodes"
                          value={(config.cluster.nodes || []).join(', ')}
                          onChange={(e) => updateConfig(['cluster', 'nodes'], e.target.value.split(',').map(s => s.trim()))}
                          placeholder="node1, node2, node3"
                        />
                      </div>
                    </div>
                  )}
                </div>
              )}
            </TabsContent>
            
            <TabsContent value="json">
              <div className="space-y-4">
                {jsonError && (
                  <Alert variant="destructive">
                    <AlertCircle className="h-4 w-4" />
                    <AlertTitle>{common('error')}</AlertTitle>
                    <AlertDescription>{jsonError}</AlertDescription>
                  </Alert>
                )}
                <Textarea
                  value={configJson}
                  onChange={(e) => setConfigJson(e.target.value)}
                  className="font-mono h-[500px]"
                  data-testid="config-json"
                />
              </div>
            </TabsContent>
          </Tabs>
        </CardContent>
        <CardFooter className="flex justify-end">
          <Button onClick={handleSaveConfig} disabled={isSaving}>
            <Save className={`mr-2 h-4 w-4 ${isSaving ? 'animate-spin' : ''}`} />
            {common('save')}
          </Button>
        </CardFooter>
      </Card>
    </div>
  )
}
