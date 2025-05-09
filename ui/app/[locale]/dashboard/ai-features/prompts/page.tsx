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
import { AlertCircle, FileText, Plus, RefreshCw } from "lucide-react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import Link from "next/link"
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

export default function PromptTemplatesPage() {
  const router = useRouter()
  const locale = useLocale()
  const { toast } = useToast()
  const t = useTranslations('promptTemplates')
  const common = useTranslations('common')
  
  const [templates, setTemplates] = useState<PromptTemplate[]>([])
  const [searchQuery, setSearchQuery] = useState("")
  const [activeTab, setActiveTab] = useState("all")
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false)
  
  // Form state for creating a new template
  const [newTemplate, setNewTemplate] = useState({
    name: "",
    description: "",
    template: "",
    systemMessage: "",
    tags: "",
    visibility: "team" as const
  })
  
  // Mock templates data
  const mockTemplates: PromptTemplate[] = [
    {
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
    {
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
    {
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
  ]
  
  // Load templates on component mount
  useEffect(() => {
    // In a real implementation, this would fetch from the API
    setTemplates(mockTemplates)
    setIsLoading(false)
  }, [])
  
  // Filter templates based on search query and active tab
  const filteredTemplates = templates.filter(template => {
    // Filter by search query
    const matchesSearch = 
      searchQuery === "" || 
      template.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      template.description.toLowerCase().includes(searchQuery.toLowerCase()) ||
      template.tags.some(tag => tag.toLowerCase().includes(searchQuery.toLowerCase()))
    
    // Filter by tab
    const matchesTab = 
      activeTab === "all" || 
      (activeTab === "active" && template.status === "active") ||
      (activeTab === "draft" && template.status === "draft") ||
      (activeTab === "archived" && template.status === "archived")
    
    return matchesSearch && matchesTab
  })
  
  // Handle creating a new template
  const handleCreateTemplate = () => {
    // Validate form
    if (!newTemplate.name || !newTemplate.template) {
      toast({
        title: common('error'),
        description: t('nameTemplateRequired'),
        variant: "destructive"
      })
      return
    }
    
    // In a real implementation, this would call the API
    const newTemplateData: PromptTemplate = {
      id: `template-${templates.length + 1}`,
      name: newTemplate.name,
      description: newTemplate.description,
      template: newTemplate.template,
      systemMessage: newTemplate.systemMessage,
      variables: [], // Would be parsed from the template in a real implementation
      tags: newTemplate.tags.split(",").map(tag => tag.trim()).filter(tag => tag !== ""),
      status: "draft",
      visibility: newTemplate.visibility,
      createdBy: "current-user",
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      version: 1,
      usageCount: 0
    }
    
    setTemplates([...templates, newTemplateData])
    setIsCreateDialogOpen(false)
    
    // Reset form
    setNewTemplate({
      name: "",
      description: "",
      template: "",
      systemMessage: "",
      tags: "",
      visibility: "team"
    })
    
    toast({
      title: t('templateCreated'),
      description: t('templateCreatedDescription')
    })
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
        <Dialog open={isCreateDialogOpen} onOpenChange={setIsCreateDialogOpen}>
          <DialogTrigger asChild>
            <Button>
              <Plus className="mr-2 h-4 w-4" />
              {t('createTemplate')}
            </Button>
          </DialogTrigger>
          <DialogContent className="sm:max-w-[600px]">
            <DialogHeader>
              <DialogTitle>{t('createNewTemplate')}</DialogTitle>
              <DialogDescription>
                {t('createNewTemplateDescription')}
              </DialogDescription>
            </DialogHeader>
            <div className="grid gap-4 py-4">
              <div className="grid gap-2">
                <Label htmlFor="name">{t('templateName')}</Label>
                <Input 
                  id="name" 
                  value={newTemplate.name}
                  onChange={(e) => setNewTemplate({...newTemplate, name: e.target.value})}
                  placeholder={t('templateNamePlaceholder')}
                />
              </div>
              
              <div className="grid gap-2">
                <Label htmlFor="description">{t('description')}</Label>
                <Input 
                  id="description" 
                  value={newTemplate.description}
                  onChange={(e) => setNewTemplate({...newTemplate, description: e.target.value})}
                  placeholder={t('descriptionPlaceholder')}
                />
              </div>
              
              <div className="grid gap-2">
                <Label htmlFor="template">{t('promptTemplate')}</Label>
                <Textarea 
                  id="template" 
                  rows={5}
                  value={newTemplate.template}
                  onChange={(e) => setNewTemplate({...newTemplate, template: e.target.value})}
                  placeholder={t('promptTemplatePlaceholder')}
                />
                <p className="text-xs text-muted-foreground">
                  {t('variablesHint')}
                </p>
              </div>
              
              <div className="grid gap-2">
                <Label htmlFor="systemMessage">{t('systemMessage')}</Label>
                <Textarea 
                  id="systemMessage" 
                  rows={3}
                  value={newTemplate.systemMessage}
                  onChange={(e) => setNewTemplate({...newTemplate, systemMessage: e.target.value})}
                  placeholder={t('systemMessagePlaceholder')}
                />
              </div>
              
              <div className="grid gap-2">
                <Label htmlFor="tags">{t('tags')}</Label>
                <Input 
                  id="tags" 
                  value={newTemplate.tags}
                  onChange={(e) => setNewTemplate({...newTemplate, tags: e.target.value})}
                  placeholder={t('tagsPlaceholder')}
                />
                <p className="text-xs text-muted-foreground">
                  {t('tagsHint')}
                </p>
              </div>
              
              <div className="grid gap-2">
                <Label htmlFor="visibility">{t('visibility')}</Label>
                <Select 
                  value={newTemplate.visibility}
                  onValueChange={(value) => setNewTemplate({...newTemplate, visibility: value as any})}
                >
                  <SelectTrigger id="visibility">
                    <SelectValue placeholder={t('selectVisibility')} />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="public">{t('public')}</SelectItem>
                    <SelectItem value="team">{t('team')}</SelectItem>
                    <SelectItem value="private">{t('private')}</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
            <DialogFooter>
              <Button variant="outline" onClick={() => setIsCreateDialogOpen(false)}>
                {common('cancel')}
              </Button>
              <Button onClick={handleCreateTemplate}>
                {t('createTemplate')}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertTitle>{common('error')}</AlertTitle>
          <AlertDescription>{error.message}</AlertDescription>
        </Alert>
      )}

      <div className="flex items-center gap-4">
        <div className="flex-1">
          <Input
            placeholder={t('searchTemplates')}
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>
        <Select defaultValue="newest">
          <SelectTrigger className="w-[180px]">
            <SelectValue placeholder={t('sortBy')} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="newest">{t('newestFirst')}</SelectItem>
            <SelectItem value="oldest">{t('oldestFirst')}</SelectItem>
            <SelectItem value="name-asc">{t('nameAsc')}</SelectItem>
            <SelectItem value="name-desc">{t('nameDesc')}</SelectItem>
            <SelectItem value="usage">{t('mostUsed')}</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <TabsList>
          <TabsTrigger value="all">{t('all')}</TabsTrigger>
          <TabsTrigger value="active">{t('active')}</TabsTrigger>
          <TabsTrigger value="draft">{t('draft')}</TabsTrigger>
          <TabsTrigger value="archived">{t('archived')}</TabsTrigger>
        </TabsList>
        <TabsContent value={activeTab} className="space-y-4 mt-4">
          {isLoading ? (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {[1, 2, 3].map((i) => (
                <Card key={i} className="animate-pulse">
                  <CardHeader className="pb-2">
                    <div className="h-5 bg-muted rounded w-1/2 mb-2"></div>
                    <div className="h-4 bg-muted rounded w-3/4"></div>
                  </CardHeader>
                  <CardContent>
                    <div className="h-20 bg-muted rounded"></div>
                  </CardContent>
                  <CardFooter className="flex justify-between border-t p-4">
                    <div className="h-4 bg-muted rounded w-1/4"></div>
                    <div className="h-8 bg-muted rounded w-1/4"></div>
                  </CardFooter>
                </Card>
              ))}
            </div>
          ) : filteredTemplates.length === 0 ? (
            <div className="flex flex-col items-center justify-center p-8 text-center">
              <h3 className="text-lg font-medium">{t('noTemplatesFound')}</h3>
              <p className="text-muted-foreground mt-1">
                {searchQuery 
                  ? t('adjustSearch')
                  : t('createFirstTemplate')}
              </p>
              {!searchQuery && (
                <Button className="mt-4" onClick={() => setIsCreateDialogOpen(true)}>
                  {t('createTemplate')}
                </Button>
              )}
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {filteredTemplates.map((template) => (
                <Card key={template.id} className="flex flex-col">
                  <CardHeader className="pb-2">
                    <div className="flex items-center justify-between">
                      <CardTitle className="text-lg">{template.name}</CardTitle>
                      <Badge variant={
                        template.status === 'active' ? 'default' : 
                        template.status === 'draft' ? 'secondary' : 'outline'
                      }>
                        {template.status === 'active' ? t('active') : 
                         template.status === 'draft' ? t('draft') : t('archived')}
                      </Badge>
                    </div>
                    <CardDescription>{template.description}</CardDescription>
                  </CardHeader>
                  <CardContent className="flex-1">
                    <div className="text-sm text-muted-foreground">
                      <div className="mb-2 line-clamp-3 font-mono text-xs bg-muted p-2 rounded">
                        {template.template}
                      </div>
                      
                      <div className="flex flex-wrap gap-1 mt-2">
                        {template.tags.map((tag) => (
                          <Badge key={tag} variant="secondary" className="text-xs">
                            {tag}
                          </Badge>
                        ))}
                      </div>
                    </div>
                  </CardContent>
                  <CardFooter className="flex justify-between border-t p-4">
                    <div className="text-sm text-muted-foreground">
                      {t('usedTimes', { count: template.usageCount })}
                    </div>
                    <Link href={`/${locale}/dashboard/ai-features/prompts/${template.id}`}>
                      <Button variant="outline" size="sm">{t('viewDetails')}</Button>
                    </Link>
                  </CardFooter>
                </Card>
              ))}
            </div>
          )}
        </TabsContent>
      </Tabs>
    </div>
  )
}
