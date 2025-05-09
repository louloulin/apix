"use client"

import { useState, useEffect } from "react"
import { useRouter } from "next/navigation"
import { useTranslations, useLocale } from 'next-intl'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import { ArrowLeft, Plus, AlertCircle } from "lucide-react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { useToast } from "@/components/ui/use-toast"

// Define plugin types
interface PluginType {
  id: string
  name: string
  description: string
  configSchema?: Record<string, any>
  defaultConfig?: Record<string, any>
}

export default function CreatePluginPage() {
  const router = useRouter()
  const locale = useLocale()
  const { toast } = useToast()
  const t = useTranslations('plugins')
  const common = useTranslations('common')
  
  const [pluginTypes, setPluginTypes] = useState<PluginType[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isCreating, setIsCreating] = useState(false)
  const [error, setError] = useState<Error | null>(null)
  
  const [formState, setFormState] = useState({
    id: "",
    type: "",
    enabled: true,
    config: "{}"
  })
  
  // Mock plugin types
  const mockPluginTypes: PluginType[] = [
    {
      id: "authentication",
      name: "Authentication",
      description: "Plugins for authenticating API requests",
      defaultConfig: { enabled: true }
    },
    {
      id: "security",
      name: "Security",
      description: "Plugins for securing API endpoints",
      defaultConfig: { enabled: true }
    },
    {
      id: "transformation",
      name: "Transformation",
      description: "Plugins for transforming request/response data",
      defaultConfig: { enabled: true }
    },
    {
      id: "business-logic",
      name: "Business Logic",
      description: "Plugins for implementing custom business logic",
      defaultConfig: { enabled: true }
    },
    {
      id: "ai-processing",
      name: "AI Processing",
      description: "Plugins for AI model processing and integration",
      defaultConfig: { enabled: true }
    },
    {
      id: "caching",
      name: "Caching",
      description: "Plugins for caching responses",
      defaultConfig: { enabled: true, ttl: 300 }
    },
    {
      id: "logging",
      name: "Logging",
      description: "Plugins for logging requests and responses",
      defaultConfig: { enabled: true, level: "info" }
    },
    {
      id: "monitoring",
      name: "Monitoring",
      description: "Plugins for monitoring API usage and performance",
      defaultConfig: { enabled: true }
    }
  ]
  
  // Load plugin types on component mount
  useEffect(() => {
    // In a real implementation, this would fetch from the API
    setPluginTypes(mockPluginTypes)
    setIsLoading(false)
  }, [])
  
  // Handle type change
  const handleTypeChange = (type: string) => {
    setFormState({
      ...formState,
      type,
      config: JSON.stringify(
        mockPluginTypes.find(t => t.id === type)?.defaultConfig || {},
        null,
        2
      )
    })
  }
  
  // Handle form submission
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    
    // Validate form
    if (!formState.id || !formState.type) {
      toast({
        title: common('error'),
        description: t('idTypeRequired'),
        variant: "destructive"
      })
      return
    }
    
    // Validate JSON
    let configObject
    try {
      configObject = JSON.parse(formState.config)
    } catch (err) {
      toast({
        title: common('error'),
        description: t('invalidJson'),
        variant: "destructive"
      })
      return
    }
    
    setIsCreating(true)
    
    try {
      // In a real implementation, this would call the API
      // Simulate API call with a timeout
      await new Promise(resolve => setTimeout(resolve, 1000))
      
      // Simulate success
      toast({
        title: t('pluginCreated'),
        description: `${formState.id} ${t('hasBeenCreated')}`
      })
      
      // Navigate to the plugins list
      router.push(`/${locale}/dashboard/plugins`)
    } catch (err) {
      setError(err instanceof Error ? err : new Error('Unknown error'))
      toast({
        title: common('error'),
        description: t('createError'),
        variant: "destructive"
      })
    } finally {
      setIsCreating(false)
    }
  }
  
  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Button 
            variant="outline" 
            size="sm" 
            onClick={() => router.push(`/${locale}/dashboard/plugins`)}
          >
            <ArrowLeft className="mr-2 h-4 w-4" />
            {t('backToPlugins')}
          </Button>
          <div>
            <h1 className="text-3xl font-bold">{t('addPlugin')}</h1>
            <p className="text-muted-foreground">
              {t('addPluginDescription')}
            </p>
          </div>
        </div>
        <Button onClick={handleSubmit} disabled={isCreating}>
          {isCreating ? (
            <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white mr-2"></div>
          ) : (
            <Plus className="mr-2 h-4 w-4" />
          )}
          {t('createPlugin')}
        </Button>
      </div>
      
      {error && (
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertTitle>{common('error')}</AlertTitle>
          <AlertDescription>{error.message}</AlertDescription>
        </Alert>
      )}
      
      <Card>
        <CardHeader>
          <CardTitle>{t('pluginInfo')}</CardTitle>
          <CardDescription>
            {t('configurePluginSettings')}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <div className="flex justify-center py-8">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-6">
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="plugin-id">{t('pluginId')}</Label>
                  <Input 
                    id="plugin-id" 
                    placeholder={t('pluginIdPlaceholder')}
                    value={formState.id}
                    onChange={(e) => setFormState({...formState, id: e.target.value})}
                    required
                  />
                  <p className="text-xs text-muted-foreground">{t('pluginIdDescription')}</p>
                </div>
                
                <div className="space-y-2">
                  <Label htmlFor="plugin-type">{t('pluginType')}</Label>
                  <Select 
                    value={formState.type} 
                    onValueChange={handleTypeChange}
                  >
                    <SelectTrigger id="plugin-type">
                      <SelectValue placeholder={t('selectPluginType')} />
                    </SelectTrigger>
                    <SelectContent>
                      {pluginTypes.map((type) => (
                        <SelectItem key={type.id} value={type.id}>
                          {type.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <p className="text-xs text-muted-foreground">{t('pluginTypeDescription')}</p>
                </div>
                
                <div className="col-span-2 space-y-2">
                  <div className="flex items-center justify-between">
                    <Label htmlFor="plugin-config">{t('configuration')} (JSON)</Label>
                    <div className="flex items-center space-x-2">
                      <Switch 
                        id="plugin-enabled"
                        checked={formState.enabled}
                        onCheckedChange={(checked) => setFormState({...formState, enabled: checked})}
                      />
                      <Label htmlFor="plugin-enabled">{t('enableAfterCreation')}</Label>
                    </div>
                  </div>
                  <Textarea 
                    id="plugin-config" 
                    value={formState.config}
                    onChange={(e) => setFormState({...formState, config: e.target.value})}
                    className="font-mono h-[400px]"
                    required
                  />
                  <p className="text-xs text-muted-foreground">
                    {t('configurationDescription')}
                  </p>
                </div>
              </div>
            </form>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
