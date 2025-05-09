"use client"

import { useState, useEffect } from "react"
import { useRouter } from "next/navigation"
import { useTranslations, useLocale } from 'next-intl'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Switch } from "@/components/ui/switch"
import { AlertCircle, Save, RefreshCw, Server, Network, Shield, Clock } from "lucide-react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { useToast } from "@/components/ui/use-toast"

export default function ConfigPage() {
  const router = useRouter()
  const locale = useLocale()
  const { toast } = useToast()
  const t = useTranslations('config')
  const common = useTranslations('common')
  
  const [activeTab, setActiveTab] = useState("server")
  const [isLoading, setIsLoading] = useState(false)
  const [isRefetching, setIsRefetching] = useState(false)
  const [isUpdating, setIsUpdating] = useState(false)
  const [error, setError] = useState<Error | null>(null)
  
  // Create a state for the form data
  const [formData, setFormData] = useState<Record<string, any>>({
    server: {
      port: 8080,
      host: "0.0.0.0",
      workerPoolSize: 20,
      compressionSupported: true,
      compressionLevel: 6
    },
    http: {
      idleTimeout: 30,
      connectTimeout: 60,
      keepAlive: true,
      maxPoolSize: 10,
      maxWaitQueueSize: 1000,
      pipelining: false
    },
    security: {
      corsEnabled: true,
      corsAllowedOrigins: "*",
      corsAllowedMethods: "GET,POST,PUT,DELETE,OPTIONS",
      rateLimitingEnabled: true,
      rateLimitRequests: 100,
      rateLimitPeriod: 60
    },
    cache: {
      enabled: true,
      maxSize: 1000,
      ttl: 300,
      cleanupInterval: 60
    }
  })
  
  // Fetch configuration data
  const fetchConfig = async () => {
    setIsRefetching(true)
    try {
      const response = await fetch('/api/config')
      if (!response.ok) {
        throw new Error(`API error: ${response.status}`)
      }
      const data = await response.json()
      setFormData(data)
      setError(null)
    } catch (err) {
      setError(err instanceof Error ? err : new Error('Unknown error'))
      toast({
        title: common('error'),
        description: err instanceof Error ? err.message : 'Unknown error',
        variant: "destructive"
      })
    } finally {
      setIsRefetching(false)
      setIsLoading(false)
    }
  }
  
  // Load config on component mount
  useEffect(() => {
    setIsLoading(true)
    fetchConfig()
  }, [])
  
  // Update config
  const updateConfig = async (config: Record<string, any>) => {
    setIsUpdating(true)
    try {
      const response = await fetch('/api/config', {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(config),
      })
      
      if (!response.ok) {
        throw new Error(`API error: ${response.status}`)
      }
      
      toast({
        title: t('saveSuccess'),
        description: t('saveSuccessDescription')
      })
      
      // Refresh the config data
      fetchConfig()
    } catch (err) {
      toast({
        title: t('saveError'),
        description: err instanceof Error ? err.message : 'Unknown error',
        variant: "destructive"
      })
    } finally {
      setIsUpdating(false)
    }
  }
  
  // Handle form submission
  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    updateConfig(formData)
  }
  
  // Handle input change
  const handleInputChange = (section: string, field: string, value: any) => {
    setFormData(prev => ({
      ...prev,
      [section]: {
        ...prev[section],
        [field]: value
      }
    }))
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
          <Button 
            variant="outline" 
            size="icon" 
            onClick={fetchConfig}
            disabled={isRefetching}
          >
            <RefreshCw className={`h-4 w-4 ${isRefetching ? 'animate-spin' : ''}`} />
          </Button>
          <Button onClick={handleSubmit} disabled={isUpdating}>
            <Save className="mr-2 h-4 w-4" />
            {common('save')}
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

      <form onSubmit={handleSubmit}>
        <Tabs defaultValue="server" value={activeTab} onValueChange={setActiveTab}>
          <TabsList className="grid w-full grid-cols-4">
            <TabsTrigger value="server" className="flex items-center gap-2">
              <Server className="h-4 w-4" />
              {t('server')}
            </TabsTrigger>
            <TabsTrigger value="http" className="flex items-center gap-2">
              <Network className="h-4 w-4" />
              {t('http')}
            </TabsTrigger>
            <TabsTrigger value="security" className="flex items-center gap-2">
              <Shield className="h-4 w-4" />
              {t('security')}
            </TabsTrigger>
            <TabsTrigger value="cache" className="flex items-center gap-2">
              <Clock className="h-4 w-4" />
              {t('cache')}
            </TabsTrigger>
          </TabsList>
          
          <TabsContent value="server">
            <Card>
              <CardHeader>
                <CardTitle>{t('serverConfig')}</CardTitle>
                <CardDescription>
                  {t('serverConfigDescription')}
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="server-port">{t('port')}</Label>
                    <Input 
                      id="server-port" 
                      type="number" 
                      value={formData.server?.port || 8080} 
                      onChange={(e) => handleInputChange('server', 'port', parseInt(e.target.value))}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="server-host">{t('host')}</Label>
                    <Input 
                      id="server-host" 
                      value={formData.server?.host || "0.0.0.0"} 
                      onChange={(e) => handleInputChange('server', 'host', e.target.value)}
                    />
                  </div>
                </div>
                
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="server-workerPoolSize">{t('workerPoolSize')}</Label>
                    <Input 
                      id="server-workerPoolSize" 
                      type="number" 
                      value={formData.server?.workerPoolSize || 20} 
                      onChange={(e) => handleInputChange('server', 'workerPoolSize', parseInt(e.target.value))}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="server-compressionLevel">{t('compressionLevel')}</Label>
                    <Input 
                      id="server-compressionLevel" 
                      type="number" 
                      min="0"
                      max="9"
                      value={formData.server?.compressionLevel || 6} 
                      onChange={(e) => handleInputChange('server', 'compressionLevel', parseInt(e.target.value))}
                    />
                  </div>
                </div>
                
                <div className="flex items-center space-x-2">
                  <Switch 
                    id="server-compressionSupported" 
                    checked={formData.server?.compressionSupported || false}
                    onCheckedChange={(checked) => handleInputChange('server', 'compressionSupported', checked)}
                  />
                  <Label htmlFor="server-compressionSupported">{t('enableCompression')}</Label>
                </div>
              </CardContent>
            </Card>
          </TabsContent>
          
          <TabsContent value="http">
            <Card>
              <CardHeader>
                <CardTitle>{t('httpConfig')}</CardTitle>
                <CardDescription>
                  {t('httpConfigDescription')}
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="http-idleTimeout">{t('idleTimeout')}</Label>
                    <Input 
                      id="http-idleTimeout" 
                      type="number" 
                      value={formData.http?.idleTimeout || 30} 
                      onChange={(e) => handleInputChange('http', 'idleTimeout', parseInt(e.target.value))}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="http-connectTimeout">{t('connectTimeout')}</Label>
                    <Input 
                      id="http-connectTimeout" 
                      type="number" 
                      value={formData.http?.connectTimeout || 60} 
                      onChange={(e) => handleInputChange('http', 'connectTimeout', parseInt(e.target.value))}
                    />
                  </div>
                </div>
                
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="http-maxPoolSize">{t('maxPoolSize')}</Label>
                    <Input 
                      id="http-maxPoolSize" 
                      type="number" 
                      value={formData.http?.maxPoolSize || 10} 
                      onChange={(e) => handleInputChange('http', 'maxPoolSize', parseInt(e.target.value))}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="http-maxWaitQueueSize">{t('maxWaitQueueSize')}</Label>
                    <Input 
                      id="http-maxWaitQueueSize" 
                      type="number" 
                      value={formData.http?.maxWaitQueueSize || 1000} 
                      onChange={(e) => handleInputChange('http', 'maxWaitQueueSize', parseInt(e.target.value))}
                    />
                  </div>
                </div>
                
                <div className="flex items-center space-x-2">
                  <Switch 
                    id="http-keepAlive" 
                    checked={formData.http?.keepAlive || true}
                    onCheckedChange={(checked) => handleInputChange('http', 'keepAlive', checked)}
                  />
                  <Label htmlFor="http-keepAlive">{t('keepAlive')}</Label>
                </div>
                
                <div className="flex items-center space-x-2">
                  <Switch 
                    id="http-pipelining" 
                    checked={formData.http?.pipelining || false}
                    onCheckedChange={(checked) => handleInputChange('http', 'pipelining', checked)}
                  />
                  <Label htmlFor="http-pipelining">{t('httpPipelining')}</Label>
                </div>
              </CardContent>
            </Card>
          </TabsContent>
          
          <TabsContent value="security">
            <Card>
              <CardHeader>
                <CardTitle>{t('securityConfig')}</CardTitle>
                <CardDescription>
                  {t('securityConfigDescription')}
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="flex items-center space-x-2">
                  <Switch 
                    id="security-corsEnabled" 
                    checked={formData.security?.corsEnabled || true}
                    onCheckedChange={(checked) => handleInputChange('security', 'corsEnabled', checked)}
                  />
                  <Label htmlFor="security-corsEnabled">{t('enableCors')}</Label>
                </div>
                
                {formData.security?.corsEnabled && (
                  <>
                    <div className="space-y-2">
                      <Label htmlFor="security-corsAllowedOrigins">{t('allowedOrigins')}</Label>
                      <Input 
                        id="security-corsAllowedOrigins" 
                        value={formData.security?.corsAllowedOrigins || "*"} 
                        onChange={(e) => handleInputChange('security', 'corsAllowedOrigins', e.target.value)}
                      />
                      <p className="text-xs text-muted-foreground">{t('allowedOriginsDescription')}</p>
                    </div>
                    
                    <div className="space-y-2">
                      <Label htmlFor="security-corsAllowedMethods">{t('allowedMethods')}</Label>
                      <Input 
                        id="security-corsAllowedMethods" 
                        value={formData.security?.corsAllowedMethods || "GET,POST,PUT,DELETE,OPTIONS"} 
                        onChange={(e) => handleInputChange('security', 'corsAllowedMethods', e.target.value)}
                      />
                      <p className="text-xs text-muted-foreground">{t('allowedMethodsDescription')}</p>
                    </div>
                  </>
                )}
                
                <div className="flex items-center space-x-2">
                  <Switch 
                    id="security-rateLimitingEnabled" 
                    checked={formData.security?.rateLimitingEnabled || true}
                    onCheckedChange={(checked) => handleInputChange('security', 'rateLimitingEnabled', checked)}
                  />
                  <Label htmlFor="security-rateLimitingEnabled">{t('enableRateLimiting')}</Label>
                </div>
                
                {formData.security?.rateLimitingEnabled && (
                  <div className="grid grid-cols-2 gap-4">
                    <div className="space-y-2">
                      <Label htmlFor="security-rateLimitRequests">{t('requestsLimit')}</Label>
                      <Input 
                        id="security-rateLimitRequests" 
                        type="number" 
                        value={formData.security?.rateLimitRequests || 100} 
                        onChange={(e) => handleInputChange('security', 'rateLimitRequests', parseInt(e.target.value))}
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="security-rateLimitPeriod">{t('periodSeconds')}</Label>
                      <Input 
                        id="security-rateLimitPeriod" 
                        type="number" 
                        value={formData.security?.rateLimitPeriod || 60} 
                        onChange={(e) => handleInputChange('security', 'rateLimitPeriod', parseInt(e.target.value))}
                      />
                    </div>
                  </div>
                )}
              </CardContent>
            </Card>
          </TabsContent>
          
          <TabsContent value="cache">
            <Card>
              <CardHeader>
                <CardTitle>{t('cacheConfig')}</CardTitle>
                <CardDescription>
                  {t('cacheConfigDescription')}
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="flex items-center space-x-2">
                  <Switch 
                    id="cache-enabled" 
                    checked={formData.cache?.enabled || true}
                    onCheckedChange={(checked) => handleInputChange('cache', 'enabled', checked)}
                  />
                  <Label htmlFor="cache-enabled">{t('enableCaching')}</Label>
                </div>
                
                {formData.cache?.enabled && (
                  <>
                    <div className="space-y-2">
                      <Label htmlFor="cache-maxSize">{t('maxCacheSize')}</Label>
                      <Input 
                        id="cache-maxSize" 
                        type="number" 
                        value={formData.cache?.maxSize || 1000} 
                        onChange={(e) => handleInputChange('cache', 'maxSize', parseInt(e.target.value))}
                      />
                      <p className="text-xs text-muted-foreground">{t('maxCacheSizeDescription')}</p>
                    </div>
                    
                    <div className="space-y-2">
                      <Label htmlFor="cache-ttl">{t('ttlSeconds')}</Label>
                      <Input 
                        id="cache-ttl" 
                        type="number" 
                        value={formData.cache?.ttl || 300} 
                        onChange={(e) => handleInputChange('cache', 'ttl', parseInt(e.target.value))}
                      />
                      <p className="text-xs text-muted-foreground">{t('ttlDescription')}</p>
                    </div>
                    
                    <div className="space-y-2">
                      <Label htmlFor="cache-cleanupInterval">{t('cleanupIntervalSeconds')}</Label>
                      <Input 
                        id="cache-cleanupInterval" 
                        type="number" 
                        value={formData.cache?.cleanupInterval || 60} 
                        onChange={(e) => handleInputChange('cache', 'cleanupInterval', parseInt(e.target.value))}
                      />
                      <p className="text-xs text-muted-foreground">{t('cleanupIntervalDescription')}</p>
                    </div>
                  </>
                )}
              </CardContent>
            </Card>
          </TabsContent>
        </Tabs>
      </form>
    </div>
  )
}
