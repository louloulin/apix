"use client"

import { useState, useEffect } from "react"
import { DashboardLayout } from "@/components/layout/dashboard-layout"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Badge } from "@/components/ui/badge"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import { PromptTemplate, mockTemplates } from "@/lib/api/prompt-templates"
import Link from "next/link"

export default function PromptTemplatesPage() {
  const [templates, setTemplates] = useState<PromptTemplate[]>([])
  const [searchQuery, setSearchQuery] = useState("")
  const [activeTab, setActiveTab] = useState("all")
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

  useEffect(() => {
    // In a real implementation, this would fetch from the API
    setTemplates(mockTemplates)
  }, [])

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

  const handleCreateTemplate = () => {
    // In a real implementation, this would call the API
    const newTemplateData: PromptTemplate = {
      id: `template-${templates.length + 1}`,
      name: newTemplate.name,
      description: newTemplate.description,
      template: newTemplate.template,
      systemMessage: newTemplate.systemMessage,
      variables: [], // Would be parsed from the template in a real implementation
      tags: newTemplate.tags.split(",").map(tag => tag.trim()),
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
  }

  return (
    <DashboardLayout>
      <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold">Prompt Templates</h1>
            <p className="text-muted-foreground">
              Create and manage reusable prompt templates
            </p>
          </div>
          <Dialog open={isCreateDialogOpen} onOpenChange={setIsCreateDialogOpen}>
            <DialogTrigger asChild>
              <Button>Create Template</Button>
            </DialogTrigger>
            <DialogContent className="sm:max-w-[600px]">
              <DialogHeader>
                <DialogTitle>Create New Prompt Template</DialogTitle>
                <DialogDescription>
                  Create a reusable prompt template with variables
                </DialogDescription>
              </DialogHeader>
              <div className="grid gap-4 py-4">
                <div className="grid gap-2">
                  <Label htmlFor="name">Template Name</Label>
                  <Input 
                    id="name" 
                    value={newTemplate.name}
                    onChange={(e) => setNewTemplate({...newTemplate, name: e.target.value})}
                    placeholder="E.g., Customer Support Response" 
                  />
                </div>
                <div className="grid gap-2">
                  <Label htmlFor="description">Description</Label>
                  <Textarea 
                    id="description" 
                    value={newTemplate.description}
                    onChange={(e) => setNewTemplate({...newTemplate, description: e.target.value})}
                    placeholder="Describe what this template is used for" 
                  />
                </div>
                <div className="grid gap-2">
                  <Label htmlFor="template">Prompt Template</Label>
                  <Textarea 
                    id="template" 
                    value={newTemplate.template}
                    onChange={(e) => setNewTemplate({...newTemplate, template: e.target.value})}
                    placeholder="Enter your prompt template with {{variables}} in double curly braces" 
                    className="min-h-[100px]"
                  />
                  <p className="text-xs text-muted-foreground">
                    Use double curly braces for variables, e.g., {{'{{'}}variable_name{{'}}'}}
                  </p>
                </div>
                <div className="grid gap-2">
                  <Label htmlFor="system-message">System Message (Optional)</Label>
                  <Textarea 
                    id="system-message" 
                    value={newTemplate.systemMessage}
                    onChange={(e) => setNewTemplate({...newTemplate, systemMessage: e.target.value})}
                    placeholder="Enter a system message to set the context for the AI" 
                  />
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div className="grid gap-2">
                    <Label htmlFor="tags">Tags</Label>
                    <Input 
                      id="tags" 
                      value={newTemplate.tags}
                      onChange={(e) => setNewTemplate({...newTemplate, tags: e.target.value})}
                      placeholder="Comma-separated tags" 
                    />
                  </div>
                  <div className="grid gap-2">
                    <Label htmlFor="visibility">Visibility</Label>
                    <Select 
                      value={newTemplate.visibility}
                      onValueChange={(value: "public" | "private" | "team") => 
                        setNewTemplate({...newTemplate, visibility: value})
                      }
                    >
                      <SelectTrigger id="visibility">
                        <SelectValue placeholder="Select visibility" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="public">Public</SelectItem>
                        <SelectItem value="private">Private</SelectItem>
                        <SelectItem value="team">Team</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                </div>
              </div>
              <DialogFooter>
                <Button variant="outline" onClick={() => setIsCreateDialogOpen(false)}>Cancel</Button>
                <Button onClick={handleCreateTemplate}>Create Template</Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </div>

        <div className="flex items-center gap-4">
          <div className="flex-1">
            <Input
              placeholder="Search templates..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>
          <Select defaultValue="newest">
            <SelectTrigger className="w-[180px]">
              <SelectValue placeholder="Sort by" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="newest">Newest First</SelectItem>
              <SelectItem value="oldest">Oldest First</SelectItem>
              <SelectItem value="name-asc">Name (A-Z)</SelectItem>
              <SelectItem value="name-desc">Name (Z-A)</SelectItem>
              <SelectItem value="usage">Most Used</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <Tabs value={activeTab} onValueChange={setActiveTab}>
          <TabsList>
            <TabsTrigger value="all">All</TabsTrigger>
            <TabsTrigger value="active">Active</TabsTrigger>
            <TabsTrigger value="draft">Draft</TabsTrigger>
            <TabsTrigger value="archived">Archived</TabsTrigger>
          </TabsList>
          <TabsContent value={activeTab} className="space-y-4 mt-4">
            {filteredTemplates.length === 0 ? (
              <div className="flex flex-col items-center justify-center p-8 text-center">
                <h3 className="text-lg font-medium">No templates found</h3>
                <p className="text-muted-foreground mt-1">
                  {searchQuery 
                    ? "Try adjusting your search query" 
                    : "Create your first prompt template to get started"}
                </p>
                {!searchQuery && (
                  <Button className="mt-4" onClick={() => setIsCreateDialogOpen(true)}>
                    Create Template
                  </Button>
                )}
              </div>
            ) : (
              <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
                {filteredTemplates.map((template) => (
                  <Card key={template.id} className="flex flex-col">
                    <CardHeader>
                      <div className="flex items-start justify-between">
                        <div>
                          <CardTitle className="flex items-center gap-2">
                            {template.name}
                            {template.status === "draft" && (
                              <Badge variant="outline">Draft</Badge>
                            )}
                            {template.status === "archived" && (
                              <Badge variant="outline" className="bg-muted">Archived</Badge>
                            )}
                          </CardTitle>
                          <CardDescription>{template.description}</CardDescription>
                        </div>
                      </div>
                    </CardHeader>
                    <CardContent className="flex-1">
                      <div className="space-y-2">
                        <div className="text-sm font-medium">Prompt Template</div>
                        <div className="text-sm text-muted-foreground line-clamp-3">
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
                        Used {template.usageCount} times
                      </div>
                      <Link href={`/dashboard/ai-features/prompts/${template.id}`}>
                        <Button variant="outline" size="sm">View Details</Button>
                      </Link>
                    </CardFooter>
                  </Card>
                ))}
              </div>
            )}
          </TabsContent>
        </Tabs>
      </div>
    </DashboardLayout>
  )
}
