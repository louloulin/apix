"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import { useTranslations, useLocale } from 'next-intl'
import { Card, CardContent, CardDescription, CardHeader, CardTitle, CardFooter } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import { Checkbox } from "@/components/ui/checkbox"
import { Textarea } from "@/components/ui/textarea"
import { ArrowLeft, Save, AlertCircle } from "lucide-react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { useToast } from "@/components/ui/use-toast"

// Define the route type
interface Route {
  id?: string
  path: string
  target: string
  methods: string[]
  plugins: string[]
  enabled: boolean
  priority: number
  type: 'llm' | 'vector' | 'other'
  description?: string
}

export default function CreateRoutePage() {
  const router = useRouter()
  const locale = useLocale()
  const { toast } = useToast()
  const t = useTranslations('routes')
  const common = useTranslations('common')
  
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<Error | null>(null)
  
  // Form state
  const [formData, setFormData] = useState<Omit<Route, 'id'>>({
    path: "",
    target: "",
    methods: ["GET"],
    plugins: [],
    enabled: true,
    priority: 100,
    type: "other",
    description: ""
  })
  
  // Available HTTP methods
  const httpMethods = ["GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"]
  
  // Available plugins
  const availablePlugins = [
    { id: "jwt-auth", name: "JWT Authentication" },
    { id: "rate-limiter", name: "Rate Limiter" },
    { id: "cors", name: "CORS" },
    { id: "logging", name: "Logging" },
    { id: "caching", name: "Caching" },
    { id: "request-transformer", name: "Request Transformer" },
    { id: "response-transformer", name: "Response Transformer" }
  ]
  
  // Handle input change
  const handleInputChange = (field: string, value: any) => {
    setFormData(prev => ({
      ...prev,
      [field]: value
    }))
  }
  
  // Handle method toggle
  const handleMethodToggle = (method: string) => {
    setFormData(prev => {
      const methods = [...prev.methods]
      
      if (methods.includes(method)) {
        return {
          ...prev,
          methods: methods.filter(m => m !== method)
        }
      } else {
        return {
          ...prev,
          methods: [...methods, method]
        }
      }
    })
  }
  
  // Handle plugin toggle
  const handlePluginToggle = (pluginId: string) => {
    setFormData(prev => {
      const plugins = [...prev.plugins]
      
      if (plugins.includes(pluginId)) {
        return {
          ...prev,
          plugins: plugins.filter(p => p !== pluginId)
        }
      } else {
        return {
          ...prev,
          plugins: [...plugins, pluginId]
        }
      }
    })
  }
  
  // Handle form submission
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    
    // Validate form
    if (!formData.path || !formData.target || formData.methods.length === 0) {
      toast({
        title: common('error'),
        description: t('requiredFieldsMissing'),
        variant: "destructive"
      })
      return
    }
    
    setIsLoading(true)
    
    try {
      // Call API to create route
      const response = await fetch('/api/routes', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(formData),
      })
      
      if (!response.ok) {
        throw new Error(`API error: ${response.status}`)
      }
      
      const data = await response.json()
      
      if (data.success) {
        toast({
          title: t('routeCreated'),
          description: t('routeCreatedDescription')
        })
        
        // Navigate back to routes list
        router.push(`/${locale}/dashboard/routes`)
      } else {
        throw new Error(data.error || 'Unknown error')
      }
    } catch (err) {
      setError(err instanceof Error ? err : new Error('Unknown error'))
      toast({
        title: common('error'),
        description: err instanceof Error ? err.message : 'Unknown error',
        variant: "destructive"
      })
    } finally {
      setIsLoading(false)
    }
  }
  
  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Button 
            variant="outline" 
            size="sm" 
            onClick={() => router.push(`/${locale}/dashboard/routes`)}
          >
            <ArrowLeft className="mr-2 h-4 w-4" />
            {t('backToRoutes')}
          </Button>
          <div>
            <h1 className="text-3xl font-bold">{t('addRoute')}</h1>
            <p className="text-muted-foreground">
              {t('addRouteDescription')}
            </p>
          </div>
        </div>
        <Button onClick={handleSubmit} disabled={isLoading}>
          {isLoading ? (
            <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white mr-2"></div>
          ) : (
            <Save className="mr-2 h-4 w-4" />
          )}
          {t('createRoute')}
        </Button>
      </div>
      
      {error && (
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertTitle>{common('error')}</AlertTitle>
          <AlertDescription>{error.message}</AlertDescription>
        </Alert>
      )}
      
      <form onSubmit={handleSubmit}>
        <Card>
          <CardHeader>
            <CardTitle>{t('routeInfo')}</CardTitle>
            <CardDescription>
              {t('routeInfoDescription')}
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-6">
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="path">{t('path')}</Label>
                <Input 
                  id="path" 
                  placeholder="/api/v1/resource"
                  value={formData.path}
                  onChange={(e) => handleInputChange('path', e.target.value)}
                  required
                />
                <p className="text-xs text-muted-foreground">{t('pathDescription')}</p>
              </div>
              
              <div className="space-y-2">
                <Label htmlFor="target">{t('target')}</Label>
                <Input 
                  id="target" 
                  placeholder="https://api.example.com/resource"
                  value={formData.target}
                  onChange={(e) => handleInputChange('target', e.target.value)}
                  required
                />
                <p className="text-xs text-muted-foreground">{t('targetDescription')}</p>
              </div>
              
              <div className="space-y-2">
                <Label htmlFor="type">{t('routeType')}</Label>
                <Select 
                  value={formData.type} 
                  onValueChange={(value) => handleInputChange('type', value)}
                >
                  <SelectTrigger id="type">
                    <SelectValue placeholder={t('selectRouteType')} />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="llm">{t('llmRoute')}</SelectItem>
                    <SelectItem value="vector">{t('vectorRoute')}</SelectItem>
                    <SelectItem value="other">{t('otherRoute')}</SelectItem>
                  </SelectContent>
                </Select>
                <p className="text-xs text-muted-foreground">{t('routeTypeDescription')}</p>
              </div>
              
              <div className="space-y-2">
                <Label htmlFor="priority">{t('priority')}</Label>
                <Input 
                  id="priority" 
                  type="number"
                  min="1"
                  max="1000"
                  value={formData.priority}
                  onChange={(e) => handleInputChange('priority', parseInt(e.target.value))}
                />
                <p className="text-xs text-muted-foreground">{t('priorityDescription')}</p>
              </div>
              
              <div className="col-span-2 space-y-2">
                <Label htmlFor="description">{t('description')}</Label>
                <Textarea 
                  id="description" 
                  placeholder={t('descriptionPlaceholder')}
                  value={formData.description}
                  onChange={(e) => handleInputChange('description', e.target.value)}
                  rows={2}
                />
              </div>
            </div>
            
            <div className="space-y-2">
              <Label>{t('methods')}</Label>
              <div className="flex flex-wrap gap-2 pt-2">
                {httpMethods.map(method => (
                  <div key={method} className="flex items-center space-x-2">
                    <Checkbox 
                      id={`method-${method}`} 
                      checked={formData.methods.includes(method)}
                      onCheckedChange={() => handleMethodToggle(method)}
                    />
                    <Label 
                      htmlFor={`method-${method}`}
                      className="text-sm font-normal"
                    >
                      {method}
                    </Label>
                  </div>
                ))}
              </div>
              <p className="text-xs text-muted-foreground">{t('methodsDescription')}</p>
            </div>
            
            <div className="space-y-2">
              <Label>{t('plugins')}</Label>
              <div className="grid grid-cols-2 md:grid-cols-3 gap-2 pt-2">
                {availablePlugins.map(plugin => (
                  <div key={plugin.id} className="flex items-center space-x-2">
                    <Checkbox 
                      id={`plugin-${plugin.id}`} 
                      checked={formData.plugins.includes(plugin.id)}
                      onCheckedChange={() => handlePluginToggle(plugin.id)}
                    />
                    <Label 
                      htmlFor={`plugin-${plugin.id}`}
                      className="text-sm font-normal"
                    >
                      {plugin.name}
                    </Label>
                  </div>
                ))}
              </div>
              <p className="text-xs text-muted-foreground">{t('pluginsDescription')}</p>
            </div>
            
            <div className="flex items-center space-x-2">
              <Switch 
                id="enabled" 
                checked={formData.enabled}
                onCheckedChange={(checked) => handleInputChange('enabled', checked)}
              />
              <Label htmlFor="enabled">{t('enableRoute')}</Label>
            </div>
          </CardContent>
          <CardFooter className="flex justify-end space-x-2">
            <Button 
              variant="outline" 
              onClick={() => router.push(`/${locale}/dashboard/routes`)}
            >
              {common('cancel')}
            </Button>
            <Button type="submit" disabled={isLoading}>
              {isLoading ? (
                <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white mr-2"></div>
              ) : (
                <Save className="mr-2 h-4 w-4" />
              )}
              {t('createRoute')}
            </Button>
          </CardFooter>
        </Card>
      </form>
    </div>
  )
}
