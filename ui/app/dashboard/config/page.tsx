"use client"

import { useState } from "react"
import { DashboardLayout } from "@/components/layout/dashboard-layout"
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Switch } from "@/components/ui/switch"
import { Slider } from "@/components/ui/slider"
import { AlertCircle, Save, RefreshCw, Server, Network, Shield, Clock } from "lucide-react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { useToast } from "@/components/ui/use-toast"
import { useApiData, useApiMutation } from "@/lib/hooks/use-api-data"
import { configApi } from "@/lib/api-client"

export default function ConfigPage() {
  const { toast } = useToast()
  const [activeTab, setActiveTab] = useState("general")
  
  // Fetch configuration data
  const { 
    data: configData, 
    isLoading, 
    error, 
    refetch,
    isRefetching
  } = useApiData(
    () => configApi.getConfig(),
    {
      onError: (err) => {
        toast({
          title: "Error fetching configuration",
          description: err.message,
          variant: "destructive"
        })
      }
    }
  )
  
  // Create a state for the form data
  const [formData, setFormData] = useState<Record<string, any>>({
    server: {
      port: 8080,
      host: "0.0.0.0",
      workerPoolSize: 20,
      maxWebsocketFrameSize: 65536,
      maxWebsocketMessageSize: 262144,
      websocketSubProtocols: "",
      compressionSupported: true,
      compressionLevel: 6,
      decoderInitialBufferSize: 128,
      maxInitialLineLength: 4096,
      maxHeaderSize: 8192,
      maxChunkSize: 8192,
      maxFormAttributeSize: 8192,
      maxFormAttributes: 1000
    },
    http: {
      idleTimeout: 30,
      connectTimeout: 60,
      keepAlive: true,
      maxPoolSize: 10,
      maxWaitQueueSize: 1000,
      pipelining: false,
      keepAliveTimeout: 60,
      readIdleTimeout: 0,
      writeIdleTimeout: 0,
      idleTimeoutUnit: "SECONDS"
    },
    security: {
      corsEnabled: true,
      corsAllowedOrigins: "*",
      corsAllowedMethods: "GET,POST,PUT,DELETE,OPTIONS",
      corsAllowedHeaders: "*",
      corsAllowCredentials: true,
      csrfProtectionEnabled: false,
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
  
  // Update form data when config data is loaded
  useState(() => {
    if (configData) {
      setFormData(configData)
    }
  })
  
  // Update config mutation
  const { 
    mutate: updateConfig, 
    isLoading: isUpdating 
  } = useApiMutation(
    (config: Record<string, any>) => configApi.updateConfig(config),
    {
      onSuccess: () => {
        toast({
          title: "Configuration updated",
          description: "The gateway configuration has been updated successfully."
        })
        refetch()
      },
      onError: (error) => {
        toast({
          title: "Error",
          description: `Failed to update configuration: ${error.message}`,
          variant: "destructive"
        })
      }
    }
  )
  
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
    <DashboardLayout>
      <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold">Gateway Configuration</h1>
            <p className="text-muted-foreground">
              Configure the core settings of your API Gateway
            </p>
          </div>
          <div className="flex gap-2">
            <Button 
              variant="outline" 
              size="icon" 
              onClick={() => refetch()}
              disabled={isRefetching}
            >
              <RefreshCw className={`h-4 w-4 ${isRefetching ? 'animate-spin' : ''}`} />
            </Button>
            <Button onClick={handleSubmit} disabled={isUpdating}>
              <Save className="mr-2 h-4 w-4" />
              Save Configuration
            </Button>
          </div>
        </div>

        {error && (
          <Alert variant="destructive">
            <AlertCircle className="h-4 w-4" />
            <AlertTitle>Error</AlertTitle>
            <AlertDescription>{error.message}</AlertDescription>
          </Alert>
        )}

        <form onSubmit={handleSubmit}>
          <Tabs defaultValue="general" value={activeTab} onValueChange={setActiveTab}>
            <TabsList className="grid w-full grid-cols-4">
              <TabsTrigger value="server" className="flex items-center gap-2">
                <Server className="h-4 w-4" />
                Server
              </TabsTrigger>
              <TabsTrigger value="http" className="flex items-center gap-2">
                <Network className="h-4 w-4" />
                HTTP
              </TabsTrigger>
              <TabsTrigger value="security" className="flex items-center gap-2">
                <Shield className="h-4 w-4" />
                Security
              </TabsTrigger>
              <TabsTrigger value="cache" className="flex items-center gap-2">
                <Clock className="h-4 w-4" />
                Cache
              </TabsTrigger>
            </TabsList>
            
            <TabsContent value="server">
              <Card>
                <CardHeader>
                  <CardTitle>Server Configuration</CardTitle>
                  <CardDescription>
                    Configure the server settings for your API Gateway
                  </CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div className="grid grid-cols-2 gap-4">
                    <div className="space-y-2">
                      <Label htmlFor="server-port">Port</Label>
                      <Input 
                        id="server-port" 
                        type="number" 
                        value={formData.server?.port || 8080} 
                        onChange={(e) => handleInputChange('server', 'port', parseInt(e.target.value))}
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="server-host">Host</Label>
                      <Input 
                        id="server-host" 
                        value={formData.server?.host || "0.0.0.0"} 
                        onChange={(e) => handleInputChange('server', 'host', e.target.value)}
                      />
                    </div>
                  </div>
                  
                  <div className="grid grid-cols-2 gap-4">
                    <div className="space-y-2">
                      <Label htmlFor="server-workerPoolSize">Worker Pool Size</Label>
                      <Input 
                        id="server-workerPoolSize" 
                        type="number" 
                        value={formData.server?.workerPoolSize || 20} 
                        onChange={(e) => handleInputChange('server', 'workerPoolSize', parseInt(e.target.value))}
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="server-compressionLevel">Compression Level (0-9)</Label>
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
                    <Label htmlFor="server-compressionSupported">Enable Compression</Label>
                  </div>
                </CardContent>
              </Card>
            </TabsContent>
            
            <TabsContent value="http">
              <Card>
                <CardHeader>
                  <CardTitle>HTTP Configuration</CardTitle>
                  <CardDescription>
                    Configure HTTP client settings for your API Gateway
                  </CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div className="grid grid-cols-2 gap-4">
                    <div className="space-y-2">
                      <Label htmlFor="http-idleTimeout">Idle Timeout (seconds)</Label>
                      <Input 
                        id="http-idleTimeout" 
                        type="number" 
                        value={formData.http?.idleTimeout || 30} 
                        onChange={(e) => handleInputChange('http', 'idleTimeout', parseInt(e.target.value))}
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="http-connectTimeout">Connect Timeout (seconds)</Label>
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
                      <Label htmlFor="http-maxPoolSize">Max Pool Size</Label>
                      <Input 
                        id="http-maxPoolSize" 
                        type="number" 
                        value={formData.http?.maxPoolSize || 10} 
                        onChange={(e) => handleInputChange('http', 'maxPoolSize', parseInt(e.target.value))}
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="http-maxWaitQueueSize">Max Wait Queue Size</Label>
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
                    <Label htmlFor="http-keepAlive">Keep Alive</Label>
                  </div>
                  
                  <div className="flex items-center space-x-2">
                    <Switch 
                      id="http-pipelining" 
                      checked={formData.http?.pipelining || false}
                      onCheckedChange={(checked) => handleInputChange('http', 'pipelining', checked)}
                    />
                    <Label htmlFor="http-pipelining">HTTP Pipelining</Label>
                  </div>
                </CardContent>
              </Card>
            </TabsContent>
            
            <TabsContent value="security">
              <Card>
                <CardHeader>
                  <CardTitle>Security Configuration</CardTitle>
                  <CardDescription>
                    Configure security settings for your API Gateway
                  </CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div className="flex items-center space-x-2">
                    <Switch 
                      id="security-corsEnabled" 
                      checked={formData.security?.corsEnabled || true}
                      onCheckedChange={(checked) => handleInputChange('security', 'corsEnabled', checked)}
                    />
                    <Label htmlFor="security-corsEnabled">Enable CORS</Label>
                  </div>
                  
                  {formData.security?.corsEnabled && (
                    <>
                      <div className="space-y-2">
                        <Label htmlFor="security-corsAllowedOrigins">Allowed Origins</Label>
                        <Input 
                          id="security-corsAllowedOrigins" 
                          value={formData.security?.corsAllowedOrigins || "*"} 
                          onChange={(e) => handleInputChange('security', 'corsAllowedOrigins', e.target.value)}
                        />
                        <p className="text-xs text-muted-foreground">Comma-separated list of allowed origins, or * for all</p>
                      </div>
                      
                      <div className="space-y-2">
                        <Label htmlFor="security-corsAllowedMethods">Allowed Methods</Label>
                        <Input 
                          id="security-corsAllowedMethods" 
                          value={formData.security?.corsAllowedMethods || "GET,POST,PUT,DELETE,OPTIONS"} 
                          onChange={(e) => handleInputChange('security', 'corsAllowedMethods', e.target.value)}
                        />
                        <p className="text-xs text-muted-foreground">Comma-separated list of allowed HTTP methods</p>
                      </div>
                    </>
                  )}
                  
                  <div className="flex items-center space-x-2">
                    <Switch 
                      id="security-rateLimitingEnabled" 
                      checked={formData.security?.rateLimitingEnabled || true}
                      onCheckedChange={(checked) => handleInputChange('security', 'rateLimitingEnabled', checked)}
                    />
                    <Label htmlFor="security-rateLimitingEnabled">Enable Rate Limiting</Label>
                  </div>
                  
                  {formData.security?.rateLimitingEnabled && (
                    <div className="grid grid-cols-2 gap-4">
                      <div className="space-y-2">
                        <Label htmlFor="security-rateLimitRequests">Requests Limit</Label>
                        <Input 
                          id="security-rateLimitRequests" 
                          type="number" 
                          value={formData.security?.rateLimitRequests || 100} 
                          onChange={(e) => handleInputChange('security', 'rateLimitRequests', parseInt(e.target.value))}
                        />
                      </div>
                      <div className="space-y-2">
                        <Label htmlFor="security-rateLimitPeriod">Period (seconds)</Label>
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
                  <CardTitle>Cache Configuration</CardTitle>
                  <CardDescription>
                    Configure caching settings for your API Gateway
                  </CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div className="flex items-center space-x-2">
                    <Switch 
                      id="cache-enabled" 
                      checked={formData.cache?.enabled || true}
                      onCheckedChange={(checked) => handleInputChange('cache', 'enabled', checked)}
                    />
                    <Label htmlFor="cache-enabled">Enable Caching</Label>
                  </div>
                  
                  {formData.cache?.enabled && (
                    <>
                      <div className="space-y-2">
                        <Label htmlFor="cache-maxSize">Max Cache Size</Label>
                        <Input 
                          id="cache-maxSize" 
                          type="number" 
                          value={formData.cache?.maxSize || 1000} 
                          onChange={(e) => handleInputChange('cache', 'maxSize', parseInt(e.target.value))}
                        />
                        <p className="text-xs text-muted-foreground">Maximum number of entries in the cache</p>
                      </div>
                      
                      <div className="space-y-2">
                        <Label htmlFor="cache-ttl">TTL (seconds)</Label>
                        <Input 
                          id="cache-ttl" 
                          type="number" 
                          value={formData.cache?.ttl || 300} 
                          onChange={(e) => handleInputChange('cache', 'ttl', parseInt(e.target.value))}
                        />
                        <p className="text-xs text-muted-foreground">Time to live for cache entries</p>
                      </div>
                      
                      <div className="space-y-2">
                        <Label htmlFor="cache-cleanupInterval">Cleanup Interval (seconds)</Label>
                        <Input 
                          id="cache-cleanupInterval" 
                          type="number" 
                          value={formData.cache?.cleanupInterval || 60} 
                          onChange={(e) => handleInputChange('cache', 'cleanupInterval', parseInt(e.target.value))}
                        />
                        <p className="text-xs text-muted-foreground">Interval for cache cleanup</p>
                      </div>
                    </>
                  )}
                </CardContent>
              </Card>
            </TabsContent>
          </Tabs>
        </form>
      </div>
    </DashboardLayout>
  )
}
