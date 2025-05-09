"use client"

import { useState, useEffect } from "react"
import { useRouter } from "next/navigation"
import { useTranslations, useLocale } from 'next-intl'
import { Card, CardContent, CardDescription, CardHeader, CardTitle, CardFooter } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog"
import { Badge } from "@/components/ui/badge"
import { Textarea } from "@/components/ui/textarea"
import { AlertCircle, ArrowLeft, FileText, Pencil, Trash2, Play, Save, X } from "lucide-react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Separator } from "@/components/ui/separator"
import { useToast } from "@/components/ui/use-toast"

// Define the prompt template type
interface TemplateVariable {
  name: string
  description: string
  required: boolean
  defaultValue?: string
  type: 'string' | 'number' | 'boolean' | 'array' | 'object'
  options?: string[]
}

interface PromptTemplate {
  id: string
  name: string
  description: string
  template: string
  systemMessage?: string
  variables: TemplateVariable[]
  tags: string[]
  status: 'active' | 'draft' | 'archived'
  visibility: 'public' | 'private' | 'team'
  createdBy: string
  createdAt: string
  updatedAt: string
  version: number
  usageCount: number
}

export default function PromptTemplateDetailPage({ params }: { params: { id: string } }) {
  const router = useRouter()
  const locale = useLocale()
  const { toast } = useToast()
  const t = useTranslations('promptTemplates')
  const common = useTranslations('common')
  
  const [template, setTemplate] = useState<PromptTemplate | null>(null)
  const [isEditing, setIsEditing] = useState(false)
  const [activeTab, setActiveTab] = useState("details")
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)
  const [editedTemplate, setEditedTemplate] = useState<Partial<PromptTemplate>>({})
  const [isDeleteDialogOpen, setIsDeleteDialogOpen] = useState(false)
  const [isTestDialogOpen, setIsTestDialogOpen] = useState(false)
  const [testVariables, setTestVariables] = useState<Record<string, string>>({})
  const [testResult, setTestResult] = useState("")
  
  // Mock templates data
  const mockTemplates: Record<string, PromptTemplate> = {
    "template-1": {
      id: "template-1",
      name: "Customer Support Response",
      description: "Template for generating customer support responses",
      template: "Please help the customer with their {{issue}} regarding {{product}}. The customer's tone is {{tone}}. Respond in a {{style}} manner.",
      systemMessage: "You are a helpful customer support agent for our company. Always be polite and professional.",
      variables: [
        {
          name: "issue",
          description: "The customer's issue",
          required: true,
          type: "string"
        },
        {
          name: "product",
          description: "The product the customer is having issues with",
          required: true,
          type: "string"
        },
        {
          name: "tone",
          description: "The customer's tone",
          required: false,
          defaultValue: "neutral",
          type: "string",
          options: ["angry", "confused", "neutral", "happy"]
        },
        {
          name: "style",
          description: "The style of the response",
          required: false,
          defaultValue: "professional",
          type: "string",
          options: ["professional", "friendly", "technical", "simple"]
        }
      ],
      tags: ["customer-support", "response", "service"],
      status: "active",
      visibility: "team",
      createdBy: "admin",
      createdAt: "2023-06-15T10:30:00Z",
      updatedAt: "2023-06-15T10:30:00Z",
      version: 1,
      usageCount: 128
    },
    "template-2": {
      id: "template-2",
      name: "Product Description Generator",
      description: "Template for generating product descriptions",
      template: "Generate a {{length}} product description for {{product_name}}, which is a {{product_type}}. The target audience is {{audience}}. The key features are: {{features}}.",
      systemMessage: "You are a marketing copywriter specializing in compelling product descriptions.",
      variables: [
        {
          name: "length",
          description: "The length of the description",
          required: false,
          defaultValue: "medium",
          type: "string",
          options: ["short", "medium", "long"]
        },
        {
          name: "product_name",
          description: "The name of the product",
          required: true,
          type: "string"
        },
        {
          name: "product_type",
          description: "The type of product",
          required: true,
          type: "string"
        },
        {
          name: "audience",
          description: "The target audience",
          required: true,
          type: "string"
        },
        {
          name: "features",
          description: "Key features of the product",
          required: true,
          type: "string"
        }
      ],
      tags: ["marketing", "product", "description"],
      status: "active",
      visibility: "team",
      createdBy: "admin",
      createdAt: "2023-06-10T14:20:00Z",
      updatedAt: "2023-06-12T09:15:00Z",
      version: 2,
      usageCount: 87
    },
    "template-3": {
      id: "template-3",
      name: "Email Draft",
      description: "Template for generating professional email drafts",
      template: "Write a professional email to {{recipient}} regarding {{subject}}. The tone should be {{tone}} and the purpose is to {{purpose}}. Include the following points: {{points}}.",
      systemMessage: "You are a professional email writer with excellent communication skills.",
      variables: [
        {
          name: "recipient",
          description: "The recipient of the email",
          required: true,
          type: "string"
        },
        {
          name: "subject",
          description: "The subject of the email",
          required: true,
          type: "string"
        },
        {
          name: "tone",
          description: "The tone of the email",
          required: false,
          defaultValue: "professional",
          type: "string",
          options: ["professional", "friendly", "urgent", "formal"]
        },
        {
          name: "purpose",
          description: "The purpose of the email",
          required: true,
          type: "string"
        },
        {
          name: "points",
          description: "Key points to include",
          required: true,
          type: "string"
        }
      ],
      tags: ["email", "communication", "draft"],
      status: "draft",
      visibility: "private",
      createdBy: "user1",
      createdAt: "2023-07-05T11:45:00Z",
      updatedAt: "2023-07-05T11:45:00Z",
      version: 1,
      usageCount: 12
    }
  }
  
  // Load template on component mount
  useEffect(() => {
    // In a real implementation, this would fetch from the API
    const templateData = mockTemplates[params.id]
    
    if (templateData) {
      setTemplate(templateData)
      setEditedTemplate(templateData)
      
      // Initialize test variables with default values
      const initialTestVars: Record<string, string> = {}
      templateData.variables.forEach(variable => {
        initialTestVars[variable.name] = variable.defaultValue || ""
      })
      setTestVariables(initialTestVars)
    } else {
      setError(new Error(t('templateNotFound')))
    }
    
    setIsLoading(false)
  }, [params.id, t])
  
  // Handle saving changes
  const handleSaveChanges = () => {
    if (!template) return
    
    // Validate form
    if (!editedTemplate.name || !editedTemplate.template) {
      toast({
        title: common('error'),
        description: t('nameTemplateRequired'),
        variant: "destructive"
      })
      return
    }
    
    // Update template
    const updatedTemplate: PromptTemplate = {
      ...template,
      ...editedTemplate,
      updatedAt: new Date().toISOString(),
      version: template.version + 1
    }
    
    setTemplate(updatedTemplate)
    setIsEditing(false)
    
    toast({
      title: t('templateUpdated'),
      description: t('templateUpdatedDescription')
    })
  }
  
  // Handle deleting template
  const handleDeleteTemplate = () => {
    if (!template) return
    
    // In a real implementation, this would call the API
    setIsDeleteDialogOpen(false)
    
    toast({
      title: t('templateDeleted'),
      description: t('templateDeletedDescription')
    })
    
    // Navigate back to templates list
    router.push(`/${locale}/dashboard/ai-features/prompts`)
  }
  
  // Handle testing template
  const handleTestTemplate = () => {
    if (!template) return
    
    // Check if all required variables are filled
    const missingRequiredVars = template.variables
      .filter(v => v.required && (!testVariables[v.name] || testVariables[v.name].trim() === ""))
      .map(v => v.name)
    
    if (missingRequiredVars.length > 0) {
      toast({
        title: common('error'),
        description: t('requiredVariablesMissing', { variables: missingRequiredVars.join(", ") }),
        variant: "destructive"
      })
      return
    }
    
    // Apply variables to template
    let result = template.template
    Object.entries(testVariables).forEach(([key, value]) => {
      result = result.replace(new RegExp(`{{${key}}}`, 'g'), value)
    })
    
    setTestResult(result)
  }
  
  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-[50vh]">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
      </div>
    )
  }
  
  if (error || !template) {
    return (
      <div className="flex flex-col items-center justify-center h-[50vh] gap-4">
        <Alert variant="destructive" className="max-w-md">
          <AlertCircle className="h-4 w-4" />
          <AlertTitle>{common('error')}</AlertTitle>
          <AlertDescription>{error?.message || t('templateNotFound')}</AlertDescription>
        </Alert>
        <Button variant="outline" onClick={() => router.push(`/${locale}/dashboard/ai-features/prompts`)}>
          <ArrowLeft className="mr-2 h-4 w-4" />
          {t('backToTemplates')}
        </Button>
      </div>
    )
  }
  
  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Button variant="outline" size="icon" onClick={() => router.push(`/${locale}/dashboard/ai-features/prompts`)}>
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <div>
            <h1 className="text-3xl font-bold">{isEditing ? t('editTemplate') : template.name}</h1>
            <p className="text-muted-foreground">
              {isEditing ? t('editTemplateDescription') : template.description}
            </p>
          </div>
        </div>
        <div className="flex gap-2">
          {isEditing ? (
            <>
              <Button variant="outline" onClick={() => setIsEditing(false)}>
                <X className="mr-2 h-4 w-4" />
                {common('cancel')}
              </Button>
              <Button onClick={handleSaveChanges}>
                <Save className="mr-2 h-4 w-4" />
                {common('save')}
              </Button>
            </>
          ) : (
            <>
              <Dialog open={isTestDialogOpen} onOpenChange={setIsTestDialogOpen}>
                <DialogTrigger asChild>
                  <Button variant="outline">
                    <Play className="mr-2 h-4 w-4" />
                    {t('testTemplate')}
                  </Button>
                </DialogTrigger>
                <DialogContent className="sm:max-w-[600px]">
                  <DialogHeader>
                    <DialogTitle>{t('testTemplate')}</DialogTitle>
                    <DialogDescription>
                      {t('testTemplateDescription')}
                    </DialogDescription>
                  </DialogHeader>
                  <div className="grid gap-4 py-4">
                    {template.variables.map((variable) => (
                      <div key={variable.name} className="grid gap-2">
                        <Label htmlFor={`var-${variable.name}`}>
                          {variable.name}
                          {variable.required && <span className="text-destructive ml-1">*</span>}
                        </Label>
                        {variable.options ? (
                          <Select 
                            value={testVariables[variable.name] || ""}
                            onValueChange={(value) => setTestVariables({...testVariables, [variable.name]: value})}
                          >
                            <SelectTrigger id={`var-${variable.name}`}>
                              <SelectValue placeholder={variable.description} />
                            </SelectTrigger>
                            <SelectContent>
                              {variable.options.map((option) => (
                                <SelectItem key={option} value={option}>{option}</SelectItem>
                              ))}
                            </SelectContent>
                          </Select>
                        ) : (
                          <Input 
                            id={`var-${variable.name}`}
                            value={testVariables[variable.name] || ""}
                            onChange={(e) => setTestVariables({...testVariables, [variable.name]: e.target.value})}
                            placeholder={variable.description}
                          />
                        )}
                        <p className="text-xs text-muted-foreground">
                          {variable.description}
                          {variable.defaultValue && ` (${t('default')}: ${variable.defaultValue})`}
                        </p>
                      </div>
                    ))}
                    
                    {testResult && (
                      <div className="mt-4">
                        <Label>{t('result')}</Label>
                        <div className="p-4 bg-muted rounded-md mt-2 whitespace-pre-wrap">
                          {testResult}
                        </div>
                      </div>
                    )}
                  </div>
                  <DialogFooter>
                    <Button variant="outline" onClick={() => setIsTestDialogOpen(false)}>
                      {common('close')}
                    </Button>
                    <Button onClick={handleTestTemplate}>
                      {t('generateResult')}
                    </Button>
                  </DialogFooter>
                </DialogContent>
              </Dialog>
              
              <Button variant="outline" onClick={() => setIsEditing(true)}>
                <Pencil className="mr-2 h-4 w-4" />
                {common('edit')}
              </Button>
              
              <Dialog open={isDeleteDialogOpen} onOpenChange={setIsDeleteDialogOpen}>
                <DialogTrigger asChild>
                  <Button variant="destructive">
                    <Trash2 className="mr-2 h-4 w-4" />
                    {common('delete')}
                  </Button>
                </DialogTrigger>
                <DialogContent>
                  <DialogHeader>
                    <DialogTitle>{t('deleteTemplate')}</DialogTitle>
                    <DialogDescription>
                      {t('deleteTemplateConfirmation')}
                    </DialogDescription>
                  </DialogHeader>
                  <DialogFooter>
                    <Button variant="outline" onClick={() => setIsDeleteDialogOpen(false)}>
                      {common('cancel')}
                    </Button>
                    <Button variant="destructive" onClick={handleDeleteTemplate}>
                      {common('delete')}
                    </Button>
                  </DialogFooter>
                </DialogContent>
              </Dialog>
            </>
          )}
        </div>
      </div>

      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <TabsList>
          <TabsTrigger value="details">{t('details')}</TabsTrigger>
          <TabsTrigger value="variables">{t('variables')}</TabsTrigger>
          <TabsTrigger value="usage">{t('usage')}</TabsTrigger>
        </TabsList>
        
        <TabsContent value="details" className="space-y-4 mt-4">
          {isEditing ? (
            <Card>
              <CardHeader>
                <CardTitle>{t('templateDetails')}</CardTitle>
                <CardDescription>
                  {t('editTemplateDetailsDescription')}
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="grid gap-2">
                  <Label htmlFor="edit-name">{t('templateName')}</Label>
                  <Input 
                    id="edit-name" 
                    value={editedTemplate.name || ""}
                    onChange={(e) => setEditedTemplate({...editedTemplate, name: e.target.value})}
                  />
                </div>
                
                <div className="grid gap-2">
                  <Label htmlFor="edit-description">{t('description')}</Label>
                  <Input 
                    id="edit-description" 
                    value={editedTemplate.description || ""}
                    onChange={(e) => setEditedTemplate({...editedTemplate, description: e.target.value})}
                  />
                </div>
                
                <div className="grid gap-2">
                  <Label htmlFor="edit-template">{t('promptTemplate')}</Label>
                  <Textarea 
                    id="edit-template" 
                    rows={5}
                    value={editedTemplate.template || ""}
                    onChange={(e) => setEditedTemplate({...editedTemplate, template: e.target.value})}
                  />
                  <p className="text-xs text-muted-foreground">
                    {t('variablesHint')}
                  </p>
                </div>
                
                <div className="grid gap-2">
                  <Label htmlFor="edit-systemMessage">{t('systemMessage')}</Label>
                  <Textarea 
                    id="edit-systemMessage" 
                    rows={3}
                    value={editedTemplate.systemMessage || ""}
                    onChange={(e) => setEditedTemplate({...editedTemplate, systemMessage: e.target.value})}
                  />
                </div>
                
                <div className="grid gap-2">
                  <Label htmlFor="edit-tags">{t('tags')}</Label>
                  <Input 
                    id="edit-tags" 
                    value={(editedTemplate.tags || []).join(", ")}
                    onChange={(e) => setEditedTemplate({
                      ...editedTemplate, 
                      tags: e.target.value.split(",").map(tag => tag.trim()).filter(tag => tag !== "")
                    })}
                  />
                </div>
                
                <div className="grid gap-2">
                  <Label htmlFor="edit-status">{t('status')}</Label>
                  <Select 
                    value={editedTemplate.status || "draft"}
                    onValueChange={(value) => setEditedTemplate({
                      ...editedTemplate, 
                      status: value as 'active' | 'draft' | 'archived'
                    })}
                  >
                    <SelectTrigger id="edit-status">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="active">{t('active')}</SelectItem>
                      <SelectItem value="draft">{t('draft')}</SelectItem>
                      <SelectItem value="archived">{t('archived')}</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                
                <div className="grid gap-2">
                  <Label htmlFor="edit-visibility">{t('visibility')}</Label>
                  <Select 
                    value={editedTemplate.visibility || "team"}
                    onValueChange={(value) => setEditedTemplate({
                      ...editedTemplate, 
                      visibility: value as 'public' | 'private' | 'team'
                    })}
                  >
                    <SelectTrigger id="edit-visibility">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="public">{t('public')}</SelectItem>
                      <SelectItem value="team">{t('team')}</SelectItem>
                      <SelectItem value="private">{t('private')}</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </CardContent>
            </Card>
          ) : (
            <>
              <Card>
                <CardHeader>
                  <CardTitle>{t('templateDetails')}</CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <h3 className="text-sm font-medium">{t('status')}</h3>
                      <div className="mt-1">
                        <Badge variant={
                          template.status === 'active' ? 'default' : 
                          template.status === 'draft' ? 'secondary' : 'outline'
                        }>
                          {template.status === 'active' ? t('active') : 
                           template.status === 'draft' ? t('draft') : t('archived')}
                        </Badge>
                      </div>
                    </div>
                    
                    <div>
                      <h3 className="text-sm font-medium">{t('visibility')}</h3>
                      <p className="text-sm text-muted-foreground mt-1">
                        {template.visibility === 'public' ? t('public') : 
                         template.visibility === 'team' ? t('team') : t('private')}
                      </p>
                    </div>
                    
                    <div>
                      <h3 className="text-sm font-medium">{t('createdBy')}</h3>
                      <p className="text-sm text-muted-foreground mt-1">{template.createdBy}</p>
                    </div>
                    
                    <div>
                      <h3 className="text-sm font-medium">{t('version')}</h3>
                      <p className="text-sm text-muted-foreground mt-1">v{template.version}</p>
                    </div>
                    
                    <div>
                      <h3 className="text-sm font-medium">{t('createdAt')}</h3>
                      <p className="text-sm text-muted-foreground mt-1">
                        {new Date(template.createdAt).toLocaleString()}
                      </p>
                    </div>
                    
                    <div>
                      <h3 className="text-sm font-medium">{t('updatedAt')}</h3>
                      <p className="text-sm text-muted-foreground mt-1">
                        {new Date(template.updatedAt).toLocaleString()}
                      </p>
                    </div>
                  </div>
                  
                  <Separator />
                  
                  <div>
                    <h3 className="text-sm font-medium">{t('tags')}</h3>
                    <div className="flex flex-wrap gap-1 mt-1">
                      {template.tags.map((tag) => (
                        <Badge key={tag} variant="secondary" className="text-xs">
                          {tag}
                        </Badge>
                      ))}
                    </div>
                  </div>
                </CardContent>
              </Card>
              
              <Card>
                <CardHeader>
                  <CardTitle>{t('promptTemplate')}</CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="bg-muted p-4 rounded-md whitespace-pre-wrap font-mono text-sm">
                    {template.template}
                  </div>
                </CardContent>
              </Card>
              
              {template.systemMessage && (
                <Card>
                  <CardHeader>
                    <CardTitle>{t('systemMessage')}</CardTitle>
                  </CardHeader>
                  <CardContent>
                    <div className="bg-muted p-4 rounded-md whitespace-pre-wrap font-mono text-sm">
                      {template.systemMessage}
                    </div>
                  </CardContent>
                </Card>
              )}
            </>
          )}
        </TabsContent>
        
        <TabsContent value="variables" className="space-y-4 mt-4">
          <Card>
            <CardHeader>
              <CardTitle>{t('templateVariables')}</CardTitle>
              <CardDescription>
                {t('templateVariablesDescription')}
              </CardDescription>
            </CardHeader>
            <CardContent>
              {template.variables.length === 0 ? (
                <div className="text-center py-8 text-muted-foreground">
                  {t('noVariables')}
                </div>
              ) : (
                <div className="space-y-4">
                  {template.variables.map((variable) => (
                    <div key={variable.name} className="border rounded-md p-4">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <h3 className="font-medium">
                            {variable.name}
                            {variable.required && <span className="text-destructive ml-1">*</span>}
                          </h3>
                          <Badge variant="outline" className="text-xs">
                            {variable.type}
                          </Badge>
                        </div>
                        <Badge variant={variable.required ? "default" : "secondary"} className="text-xs">
                          {variable.required ? t('required') : t('optional')}
                        </Badge>
                      </div>
                      
                      <p className="text-sm text-muted-foreground mt-1">
                        {variable.description}
                      </p>
                      
                      {variable.defaultValue && (
                        <div className="mt-2">
                          <span className="text-xs text-muted-foreground">{t('defaultValue')}:</span>
                          <span className="text-xs ml-1 font-mono">{variable.defaultValue}</span>
                        </div>
                      )}
                      
                      {variable.options && variable.options.length > 0 && (
                        <div className="mt-2">
                          <span className="text-xs text-muted-foreground">{t('options')}:</span>
                          <div className="flex flex-wrap gap-1 mt-1">
                            {variable.options.map((option) => (
                              <Badge key={option} variant="outline" className="text-xs">
                                {option}
                              </Badge>
                            ))}
                          </div>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>
        
        <TabsContent value="usage" className="space-y-4 mt-4">
          <Card>
            <CardHeader>
              <CardTitle>{t('usageStatistics')}</CardTitle>
              <CardDescription>
                {t('usageStatisticsDescription')}
              </CardDescription>
            </CardHeader>
            <CardContent>
              <div className="grid grid-cols-2 gap-4">
                <div className="border rounded-md p-4">
                  <h3 className="text-sm font-medium">{t('totalUsage')}</h3>
                  <p className="text-2xl font-bold mt-1">{template.usageCount}</p>
                </div>
                
                <div className="border rounded-md p-4">
                  <h3 className="text-sm font-medium">{t('lastUsed')}</h3>
                  <p className="text-sm text-muted-foreground mt-1">
                    {template.usageCount > 0 
                      ? new Date(template.updatedAt).toLocaleString() 
                      : t('never')}
                  </p>
                </div>
              </div>
              
              <div className="mt-8 text-center text-sm text-muted-foreground">
                {t('detailedUsageStats')}
              </div>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  )
}
