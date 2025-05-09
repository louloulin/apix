"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import { DashboardLayout } from "@/components/layout/dashboard-layout"
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Switch } from "@/components/ui/switch"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { AlertCircle, ArrowLeft, Save } from "lucide-react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { useToast } from "@/components/ui/use-toast"
import { useApiData, useApiMutation } from "@/lib/hooks/use-api-data"
import { aiModelsApi } from "@/lib/api-client"
import { Checkbox } from "@/components/ui/checkbox"

export default function CreateAIRoutingRulePage() {
  const router = useRouter()
  const { toast } = useToast()
  
  // Form state
  const [formData, setFormData] = useState({
    name: "",
    priority: 50,
    condition: {
      type: "CONTAINS",
      pattern: "",
      contentTypes: ["text/plain"],
      requestTypes: ["chat"]
    },
    targetModel: "",
    enabled: true
  })
  
  // Fetch models data
  const { 
    data: modelsData, 
    isLoading: isLoadingModels, 
    error: modelsError 
  } = useApiData(
    () => aiModelsApi.getModels(),
    {
      onError: (err) => {
        toast({
          title: "Error fetching models",
          description: err.message,
          variant: "destructive"
        })
      }
    }
  )
  
  // Create rule mutation
  const { 
    mutate: createRule, 
    isLoading, 
    error 
  } = useApiMutation(
    async (data: typeof formData) => {
      return aiModelsApi.createRoutingRule(data)
    },
    {
      onSuccess: (data) => {
        toast({
          title: "Rule created",
          description: `${formData.name} has been created successfully`
        })
        router.push('/dashboard/ai/routing')
      },
      onError: (error) => {
        toast({
          title: "Error",
          description: `Failed to create rule: ${error.message}`,
          variant: "destructive"
        })
      }
    }
  )
  
  // Handle form submission
  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    
    // Validate form
    if (!formData.name) {
      toast({
        title: "Validation error",
        description: "Rule name is required",
        variant: "destructive"
      })
      return
    }
    
    if (!formData.targetModel) {
      toast({
        title: "Validation error",
        description: "Target model is required",
        variant: "destructive"
      })
      return
    }
    
    if (formData.condition.type !== "DEFAULT" && !formData.condition.pattern) {
      toast({
        title: "Validation error",
        description: "Pattern is required for non-default rules",
        variant: "destructive"
      })
      return
    }
    
    // Submit form
    createRule(formData)
  }
  
  // Handle input change
  const handleInputChange = (field: string, value: any) => {
    setFormData(prev => ({
      ...prev,
      [field]: value
    }))
  }
  
  // Handle condition change
  const handleConditionChange = (field: string, value: any) => {
    setFormData(prev => ({
      ...prev,
      condition: {
        ...prev.condition,
        [field]: value
      }
    }))
  }
  
  // Handle content type toggle
  const handleContentTypeToggle = (contentType: string, checked: boolean) => {
    setFormData(prev => {
      const contentTypes = [...prev.condition.contentTypes]
      
      if (checked && !contentTypes.includes(contentType)) {
        contentTypes.push(contentType)
      } else if (!checked && contentTypes.includes(contentType)) {
        const index = contentTypes.indexOf(contentType)
        contentTypes.splice(index, 1)
      }
      
      return {
        ...prev,
        condition: {
          ...prev.condition,
          contentTypes
        }
      }
    })
  }
  
  // Handle request type toggle
  const handleRequestTypeToggle = (requestType: string, checked: boolean) => {
    setFormData(prev => {
      const requestTypes = [...prev.condition.requestTypes]
      
      if (checked && !requestTypes.includes(requestType)) {
        requestTypes.push(requestType)
      } else if (!checked && requestTypes.includes(requestType)) {
        const index = requestTypes.indexOf(requestType)
        requestTypes.splice(index, 1)
      }
      
      return {
        ...prev,
        condition: {
          ...prev.condition,
          requestTypes
        }
      }
    })
  }
  
  // Get enabled models for selection
  const enabledModels = modelsData?.models?.filter(model => model.enabled) || []
  
  return (
    <DashboardLayout>
      <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Button variant="outline" size="icon" onClick={() => router.push('/dashboard/ai/routing')}>
              <ArrowLeft className="h-4 w-4" />
            </Button>
            <div>
              <h1 className="text-3xl font-bold">Add Routing Rule</h1>
              <p className="text-muted-foreground">
                Create a new AI routing rule
              </p>
            </div>
          </div>
        </div>

        {(error || modelsError) && (
          <Alert variant="destructive">
            <AlertCircle className="h-4 w-4" />
            <AlertTitle>Error</AlertTitle>
            <AlertDescription>{error?.message || modelsError?.message}</AlertDescription>
          </Alert>
        )}

        <form onSubmit={handleSubmit}>
          <Card className="mt-6">
            <CardHeader>
              <CardTitle>Rule Information</CardTitle>
              <CardDescription>
                Basic information about the routing rule
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="name">Rule Name <span className="text-red-500">*</span></Label>
                  <Input 
                    id="name" 
                    value={formData.name} 
                    onChange={(e) => handleInputChange('name', e.target.value)}
                    placeholder="e.g. Technical Content Rule"
                    required
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="priority">Priority</Label>
                  <Input 
                    id="priority" 
                    type="number" 
                    value={formData.priority} 
                    onChange={(e) => handleInputChange('priority', parseInt(e.target.value) || 0)}
                    placeholder="e.g. 100"
                  />
                  <p className="text-xs text-muted-foreground">Higher priority rules are evaluated first</p>
                </div>
              </div>
              
              <div className="space-y-2">
                <Label htmlFor="conditionType">Condition Type <span className="text-red-500">*</span></Label>
                <Select 
                  value={formData.condition.type} 
                  onValueChange={(value) => handleConditionChange('type', value)}
                  required
                >
                  <SelectTrigger id="conditionType">
                    <SelectValue placeholder="Select condition type" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="CONTAINS">Contains</SelectItem>
                    <SelectItem value="STARTS_WITH">Starts With</SelectItem>
                    <SelectItem value="ENDS_WITH">Ends With</SelectItem>
                    <SelectItem value="REGEX">Regex</SelectItem>
                    <SelectItem value="DEFAULT">Default (matches everything)</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              
              {formData.condition.type !== "DEFAULT" && (
                <div className="space-y-2">
                  <Label htmlFor="pattern">Pattern <span className="text-red-500">*</span></Label>
                  <Input 
                    id="pattern" 
                    value={formData.condition.pattern} 
                    onChange={(e) => handleConditionChange('pattern', e.target.value)}
                    placeholder={formData.condition.type === "REGEX" ? "e.g. \\b(code|program)\\b" : "e.g. code"}
                    required
                  />
                  <p className="text-xs text-muted-foreground">
                    {formData.condition.type === "CONTAINS" && "Text that must be contained in the request"}
                    {formData.condition.type === "STARTS_WITH" && "Text that the request must start with"}
                    {formData.condition.type === "ENDS_WITH" && "Text that the request must end with"}
                    {formData.condition.type === "REGEX" && "Regular expression pattern to match against the request"}
                  </p>
                </div>
              )}
              
              <div className="space-y-2">
                <Label>Content Types</Label>
                <div className="grid grid-cols-2 gap-4 mt-2">
                  {['text/plain', 'application/json', 'text/html', 'image/*'].map(contentType => (
                    <div key={contentType} className="flex items-center space-x-2">
                      <Checkbox 
                        id={`content-type-${contentType}`} 
                        checked={formData.condition.contentTypes.includes(contentType)}
                        onCheckedChange={(checked) => handleContentTypeToggle(contentType, checked === true)}
                      />
                      <Label htmlFor={`content-type-${contentType}`}>{contentType}</Label>
                    </div>
                  ))}
                </div>
              </div>
              
              <div className="space-y-2">
                <Label>Request Types</Label>
                <div className="grid grid-cols-2 gap-4 mt-2">
                  {['chat', 'completion', 'embedding', 'image-generation'].map(requestType => (
                    <div key={requestType} className="flex items-center space-x-2">
                      <Checkbox 
                        id={`request-type-${requestType}`} 
                        checked={formData.condition.requestTypes.includes(requestType)}
                        onCheckedChange={(checked) => handleRequestTypeToggle(requestType, checked === true)}
                      />
                      <Label htmlFor={`request-type-${requestType}`}>{requestType.split('-').map(word => word.charAt(0).toUpperCase() + word.slice(1)).join(' ')}</Label>
                    </div>
                  ))}
                </div>
              </div>
              
              <div className="space-y-2">
                <Label htmlFor="targetModel">Target Model <span className="text-red-500">*</span></Label>
                <Select 
                  value={formData.targetModel} 
                  onValueChange={(value) => handleInputChange('targetModel', value)}
                  required
                >
                  <SelectTrigger id="targetModel">
                    <SelectValue placeholder="Select target model" />
                  </SelectTrigger>
                  <SelectContent>
                    {isLoadingModels ? (
                      <SelectItem value="" disabled>Loading models...</SelectItem>
                    ) : enabledModels.length === 0 ? (
                      <SelectItem value="" disabled>No enabled models available</SelectItem>
                    ) : (
                      enabledModels.map(model => (
                        <SelectItem key={model.id} value={model.id}>{model.name}</SelectItem>
                      ))
                    )}
                  </SelectContent>
                </Select>
              </div>
              
              <div className="space-y-2">
                <Label htmlFor="enabled">Status</Label>
                <div className="flex items-center space-x-2 pt-2">
                  <Switch 
                    id="enabled" 
                    checked={formData.enabled}
                    onCheckedChange={(checked) => handleInputChange('enabled', checked)}
                  />
                  <Label htmlFor="enabled">Enabled</Label>
                </div>
              </div>
            </CardContent>
            <CardFooter className="flex justify-end space-x-2">
              <Button variant="outline" onClick={() => router.push('/dashboard/ai/routing')}>
                Cancel
              </Button>
              <Button type="submit" disabled={isLoading}>
                {isLoading ? (
                  <>
                    <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white mr-2"></div>
                    Creating...
                  </>
                ) : (
                  <>
                    <Save className="mr-2 h-4 w-4" />
                    Create Rule
                  </>
                )}
              </Button>
            </CardFooter>
          </Card>
        </form>
      </div>
    </DashboardLayout>
  )
}
