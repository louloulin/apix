"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import { DashboardLayout } from "@/components/layout/dashboard-layout"
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { Switch } from "@/components/ui/switch"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { AlertCircle, ArrowLeft, Save } from "lucide-react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { useToast } from "@/components/ui/use-toast"
import { useApiMutation } from "@/lib/hooks/use-api-data"
import { aiModelsApi } from "@/lib/api-client"

export default function CreateAIModelPage() {
  const router = useRouter()
  const { toast } = useToast()
  
  // Form state
  const [formData, setFormData] = useState({
    name: "",
    provider: "",
    description: "",
    maxTokens: 0,
    costPerToken: 0,
    capabilities: [] as string[],
    priority: 0,
    enabled: true
  })
  
  // Create model mutation
  const { 
    mutate: createModel, 
    isLoading, 
    error 
  } = useApiMutation(
    async (data: typeof formData) => {
      return aiModelsApi.createModel(data)
    },
    {
      onSuccess: (data) => {
        toast({
          title: "Model created",
          description: `${formData.name} has been created successfully`
        })
        router.push('/dashboard/ai/models')
      },
      onError: (error) => {
        toast({
          title: "Error",
          description: `Failed to create model: ${error.message}`,
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
        description: "Model name is required",
        variant: "destructive"
      })
      return
    }
    
    if (!formData.provider) {
      toast({
        title: "Validation error",
        description: "Provider is required",
        variant: "destructive"
      })
      return
    }
    
    // Submit form
    createModel(formData)
  }
  
  // Handle input change
  const handleInputChange = (field: string, value: any) => {
    setFormData(prev => ({
      ...prev,
      [field]: value
    }))
  }
  
  // Handle capability toggle
  const handleCapabilityToggle = (capability: string, checked: boolean) => {
    setFormData(prev => {
      const capabilities = [...prev.capabilities]
      
      if (checked && !capabilities.includes(capability)) {
        capabilities.push(capability)
      } else if (!checked && capabilities.includes(capability)) {
        const index = capabilities.indexOf(capability)
        capabilities.splice(index, 1)
      }
      
      return {
        ...prev,
        capabilities
      }
    })
  }
  
  return (
    <DashboardLayout>
      <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Button variant="outline" size="icon" onClick={() => router.push('/dashboard/ai/models')}>
              <ArrowLeft className="h-4 w-4" />
            </Button>
            <div>
              <h1 className="text-3xl font-bold">Add AI Model</h1>
              <p className="text-muted-foreground">
                Create a new AI model for your gateway
              </p>
            </div>
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
          <Card className="mt-6">
            <CardHeader>
              <CardTitle>Model Information</CardTitle>
              <CardDescription>
                Basic information about the AI model
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="name">Model Name <span className="text-red-500">*</span></Label>
                  <Input 
                    id="name" 
                    value={formData.name} 
                    onChange={(e) => handleInputChange('name', e.target.value)}
                    placeholder="e.g. GPT-4"
                    required
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="provider">Provider <span className="text-red-500">*</span></Label>
                  <Select 
                    value={formData.provider} 
                    onValueChange={(value) => handleInputChange('provider', value)}
                    required
                  >
                    <SelectTrigger id="provider">
                      <SelectValue placeholder="Select provider" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="OpenAI">OpenAI</SelectItem>
                      <SelectItem value="Anthropic">Anthropic</SelectItem>
                      <SelectItem value="Google">Google</SelectItem>
                      <SelectItem value="Cohere">Cohere</SelectItem>
                      <SelectItem value="Custom">Custom</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </div>
              
              <div className="space-y-2">
                <Label htmlFor="description">Description</Label>
                <Textarea 
                  id="description" 
                  value={formData.description} 
                  onChange={(e) => handleInputChange('description', e.target.value)}
                  placeholder="Describe the model's capabilities and use cases"
                  rows={3}
                />
              </div>
              
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="maxTokens">Max Tokens</Label>
                  <Input 
                    id="maxTokens" 
                    type="number" 
                    value={formData.maxTokens || ''} 
                    onChange={(e) => handleInputChange('maxTokens', parseInt(e.target.value) || 0)}
                    placeholder="e.g. 8192"
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="costPerToken">Cost Per Token ($)</Label>
                  <Input 
                    id="costPerToken" 
                    type="number" 
                    step="0.000001"
                    value={formData.costPerToken || ''} 
                    onChange={(e) => handleInputChange('costPerToken', parseFloat(e.target.value) || 0)}
                    placeholder="e.g. 0.00003"
                  />
                </div>
              </div>
              
              <div className="space-y-2">
                <Label>Capabilities</Label>
                <div className="grid grid-cols-2 gap-4 mt-2">
                  {['text-generation', 'code-generation', 'reasoning', 'creative-writing', 'summarization'].map(capability => (
                    <div key={capability} className="flex items-center space-x-2">
                      <Switch 
                        id={`capability-${capability}`} 
                        checked={formData.capabilities.includes(capability)}
                        onCheckedChange={(checked) => handleCapabilityToggle(capability, checked)}
                      />
                      <Label htmlFor={`capability-${capability}`}>{capability.split('-').map(word => word.charAt(0).toUpperCase() + word.slice(1)).join(' ')}</Label>
                    </div>
                  ))}
                </div>
              </div>
              
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="priority">Priority</Label>
                  <Input 
                    id="priority" 
                    type="number" 
                    value={formData.priority || ''} 
                    onChange={(e) => handleInputChange('priority', parseInt(e.target.value) || 0)}
                    placeholder="e.g. 100"
                  />
                  <p className="text-xs text-muted-foreground">Higher priority models are preferred in routing</p>
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
              </div>
            </CardContent>
            <CardFooter className="flex justify-end space-x-2">
              <Button variant="outline" onClick={() => router.push('/dashboard/ai/models')}>
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
                    Create Model
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
