"use client"

import { useState, useEffect } from "react"
import { useRouter } from "next/navigation"
import { DashboardLayout } from "@/components/layout/dashboard-layout"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Badge } from "@/components/ui/badge"
import { Textarea } from "@/components/ui/textarea"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog"
import { PromptTemplate, TemplateVariable, mockTemplates } from "@/lib/api/prompt-templates"
import { mockModels } from "@/lib/api/ai-models"

export default function PromptTemplateDetailPage({ params }: { params: { id: string } }) {
  const router = useRouter()
  const [template, setTemplate] = useState<PromptTemplate | null>(null)
  const [isEditing, setIsEditing] = useState(false)
  const [activeTab, setActiveTab] = useState("details")
  const [editedTemplate, setEditedTemplate] = useState<Partial<PromptTemplate>>({})
  const [isDeleteDialogOpen, setIsDeleteDialogOpen] = useState(false)
  const [isTestDialogOpen, setIsTestDialogOpen] = useState(false)
  const [testVariables, setTestVariables] = useState<Record<string, string>>({})
  
  useEffect(() => {
    // In a real implementation, this would fetch from the API
    const foundTemplate = mockTemplates.find(t => t.id === params.id)
    if (foundTemplate) {
      setTemplate(foundTemplate)
      setEditedTemplate(foundTemplate)
      
      // Initialize test variables with default values
      const initialTestVars: Record<string, string> = {}
      foundTemplate.variables.forEach(variable => {
        initialTestVars[variable.name] = variable.defaultValue || ""
      })
      setTestVariables(initialTestVars)
    }
  }, [params.id])

  const handleSaveChanges = () => {
    if (!template) return
    
    // In a real implementation, this would call the API
    const updatedTemplate = {
      ...template,
      ...editedTemplate,
      updatedAt: new Date().toISOString(),
      version: template.version + 1
    }
    
    setTemplate(updatedTemplate)
    setIsEditing(false)
  }

  const handleDeleteTemplate = () => {
    // In a real implementation, this would call the API
    setIsDeleteDialogOpen(false)
    router.push("/dashboard/ai-features/prompts")
  }

  const handleTestPrompt = () => {
    // In a real implementation, this would call the API to test the prompt
    setIsTestDialogOpen(false)
  }

  const handleVariableChange = (name: string, value: string) => {
    setTestVariables({
      ...testVariables,
      [name]: value
    })
  }

  if (!template) {
    return (
      <DashboardLayout>
        <div className="flex items-center justify-center h-[50vh]">
          <p>Loading template...</p>
        </div>
      </DashboardLayout>
    )
  }

  // Function to extract variables from template text
  const extractVariables = (text: string): string[] => {
    const regex = /{{([^{}]+)}}/g
    const matches = text.match(regex) || []
    return matches.map(match => match.replace(/{{|}}/g, ""))
  }

  // Get variables from the template
  const templateVariables = extractVariables(editedTemplate.template || template.template)

  return (
    <DashboardLayout>
      <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold">{isEditing ? "Edit Template" : template.name}</h1>
            <p className="text-muted-foreground">
              {isEditing ? "Edit your prompt template" : template.description}
            </p>
          </div>
          <div className="flex gap-2">
            {isEditing ? (
              <>
                <Button variant="outline" onClick={() => setIsEditing(false)}>Cancel</Button>
                <Button onClick={handleSaveChanges}>Save Changes</Button>
              </>
            ) : (
              <>
                <Dialog open={isTestDialogOpen} onOpenChange={setIsTestDialogOpen}>
                  <DialogTrigger asChild>
                    <Button variant="outline">Test Template</Button>
                  </DialogTrigger>
                  <DialogContent className="sm:max-w-[600px]">
                    <DialogHeader>
                      <DialogTitle>Test Prompt Template</DialogTitle>
                      <DialogDescription>
                        Fill in the variables to test this prompt template
                      </DialogDescription>
                    </DialogHeader>
                    <div className="grid gap-4 py-4">
                      {template.variables.map((variable) => (
                        <div key={variable.name} className="grid gap-2">
                          <Label htmlFor={`var-${variable.name}`}>
                            {variable.name} {variable.required && <span className="text-red-500">*</span>}
                          </Label>
                          {variable.type === "string" && variable.options ? (
                            <Select 
                              value={testVariables[variable.name] || ""}
                              onValueChange={(value) => handleVariableChange(variable.name, value)}
                            >
                              <SelectTrigger id={`var-${variable.name}`}>
                                <SelectValue placeholder={`Select ${variable.name}`} />
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
                              onChange={(e) => handleVariableChange(variable.name, e.target.value)}
                              placeholder={variable.description}
                            />
                          )}
                          <p className="text-xs text-muted-foreground">{variable.description}</p>
                        </div>
                      ))}
                    </div>
                    <DialogFooter>
                      <Button variant="outline" onClick={() => setIsTestDialogOpen(false)}>Cancel</Button>
                      <Button onClick={handleTestPrompt}>Test Prompt</Button>
                    </DialogFooter>
                  </DialogContent>
                </Dialog>
                <Button onClick={() => setIsEditing(true)}>Edit Template</Button>
              </>
            )}
          </div>
        </div>

        <Tabs value={activeTab} onValueChange={setActiveTab}>
          <TabsList>
            <TabsTrigger value="details">Details</TabsTrigger>
            <TabsTrigger value="variables">Variables</TabsTrigger>
            <TabsTrigger value="usage">Usage</TabsTrigger>
          </TabsList>
          
          <TabsContent value="details" className="space-y-4 mt-4">
            <Card>
              <CardHeader>
                <CardTitle>Template Information</CardTitle>
                <CardDescription>
                  Basic information about this prompt template
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                {isEditing ? (
                  <>
                    <div className="grid gap-2">
                      <Label htmlFor="name">Template Name</Label>
                      <Input 
                        id="name" 
                        value={editedTemplate.name || template.name}
                        onChange={(e) => setEditedTemplate({...editedTemplate, name: e.target.value})}
                      />
                    </div>
                    <div className="grid gap-2">
                      <Label htmlFor="description">Description</Label>
                      <Textarea 
                        id="description" 
                        value={editedTemplate.description || template.description}
                        onChange={(e) => setEditedTemplate({...editedTemplate, description: e.target.value})}
                      />
                    </div>
                    <div className="grid gap-2">
                      <Label htmlFor="tags">Tags</Label>
                      <Input 
                        id="tags" 
                        value={(editedTemplate.tags || template.tags).join(", ")}
                        onChange={(e) => setEditedTemplate({
                          ...editedTemplate, 
                          tags: e.target.value.split(",").map(tag => tag.trim())
                        })}
                        placeholder="Comma-separated tags"
                      />
                    </div>
                    <div className="grid grid-cols-2 gap-4">
                      <div className="grid gap-2">
                        <Label htmlFor="status">Status</Label>
                        <Select 
                          value={editedTemplate.status || template.status}
                          onValueChange={(value: "active" | "draft" | "archived") => 
                            setEditedTemplate({...editedTemplate, status: value})
                          }
                        >
                          <SelectTrigger id="status">
                            <SelectValue placeholder="Select status" />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="active">Active</SelectItem>
                            <SelectItem value="draft">Draft</SelectItem>
                            <SelectItem value="archived">Archived</SelectItem>
                          </SelectContent>
                        </Select>
                      </div>
                      <div className="grid gap-2">
                        <Label htmlFor="visibility">Visibility</Label>
                        <Select 
                          value={editedTemplate.visibility || template.visibility}
                          onValueChange={(value: "public" | "private" | "team") => 
                            setEditedTemplate({...editedTemplate, visibility: value})
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
                    <div className="grid gap-2">
                      <Label htmlFor="model">Preferred Model (Optional)</Label>
                      <Select 
                        value={editedTemplate.modelId || template.modelId || ""}
                        onValueChange={(value) => 
                          setEditedTemplate({...editedTemplate, modelId: value === "" ? undefined : value})
                        }
                      >
                        <SelectTrigger id="model">
                          <SelectValue placeholder="Select model (optional)" />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="">No specific model</SelectItem>
                          {mockModels.map((model) => (
                            <SelectItem key={model.id} value={model.id}>{model.name}</SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>
                  </>
                ) : (
                  <div className="grid gap-4">
                    <div className="grid grid-cols-2 gap-4">
                      <div>
                        <div className="text-sm font-medium">Status</div>
                        <div className="mt-1">
                          <Badge 
                            variant={template.status === "active" ? "default" : "outline"}
                            className={template.status === "archived" ? "bg-muted" : ""}
                          >
                            {template.status.charAt(0).toUpperCase() + template.status.slice(1)}
                          </Badge>
                        </div>
                      </div>
                      <div>
                        <div className="text-sm font-medium">Visibility</div>
                        <div className="mt-1">
                          <Badge variant="outline">
                            {template.visibility.charAt(0).toUpperCase() + template.visibility.slice(1)}
                          </Badge>
                        </div>
                      </div>
                    </div>
                    
                    <div>
                      <div className="text-sm font-medium">Tags</div>
                      <div className="flex flex-wrap gap-1 mt-1">
                        {template.tags.map((tag) => (
                          <Badge key={tag} variant="secondary" className="text-xs">
                            {tag}
                          </Badge>
                        ))}
                      </div>
                    </div>
                    
                    <div>
                      <div className="text-sm font-medium">Preferred Model</div>
                      <div className="text-sm text-muted-foreground mt-1">
                        {template.modelId ? 
                          mockModels.find(m => m.id === template.modelId)?.name || template.modelId : 
                          "No specific model (will use default)"}
                      </div>
                    </div>
                    
                    <div>
                      <div className="text-sm font-medium">Created By</div>
                      <div className="text-sm text-muted-foreground mt-1">
                        {template.createdBy}
                      </div>
                    </div>
                    
                    <div className="grid grid-cols-3 gap-4">
                      <div>
                        <div className="text-sm font-medium">Created</div>
                        <div className="text-sm text-muted-foreground mt-1">
                          {new Date(template.createdAt).toLocaleDateString()}
                        </div>
                      </div>
                      <div>
                        <div className="text-sm font-medium">Last Updated</div>
                        <div className="text-sm text-muted-foreground mt-1">
                          {new Date(template.updatedAt).toLocaleDateString()}
                        </div>
                      </div>
                      <div>
                        <div className="text-sm font-medium">Version</div>
                        <div className="text-sm text-muted-foreground mt-1">
                          v{template.version}
                        </div>
                      </div>
                    </div>
                  </div>
                )}
              </CardContent>
            </Card>
            
            <Card>
              <CardHeader>
                <CardTitle>Prompt Template</CardTitle>
                <CardDescription>
                  The template text with variables in double curly braces
                </CardDescription>
              </CardHeader>
              <CardContent>
                {isEditing ? (
                  <div className="grid gap-4">
                    <div className="grid gap-2">
                      <Label htmlFor="template">Template Text</Label>
                      <Textarea 
                        id="template" 
                        value={editedTemplate.template || template.template}
                        onChange={(e) => setEditedTemplate({...editedTemplate, template: e.target.value})}
                        className="min-h-[150px] font-mono text-sm"
                      />
                      <p className="text-xs text-muted-foreground">
                        Use double curly braces for variables, e.g., {{'{{'}}variable_name{{'}}'}}
                      </p>
                    </div>
                    
                    <div className="grid gap-2">
                      <Label htmlFor="system-message">System Message (Optional)</Label>
                      <Textarea 
                        id="system-message" 
                        value={editedTemplate.systemMessage || template.systemMessage || ""}
                        onChange={(e) => setEditedTemplate({
                          ...editedTemplate, 
                          systemMessage: e.target.value === "" ? undefined : e.target.value
                        })}
                        className="min-h-[100px]"
                        placeholder="Enter a system message to set the context for the AI"
                      />
                    </div>
                  </div>
                ) : (
                  <div className="space-y-4">
                    <div>
                      <div className="text-sm font-medium">Template Text</div>
                      <div className="mt-2 rounded-md bg-muted p-4 font-mono text-sm">
                        {template.template}
                      </div>
                    </div>
                    
                    {template.systemMessage && (
                      <div>
                        <div className="text-sm font-medium">System Message</div>
                        <div className="mt-2 rounded-md bg-muted p-4 text-sm">
                          {template.systemMessage}
                        </div>
                      </div>
                    )}
                  </div>
                )}
              </CardContent>
            </Card>
            
            {!isEditing && (
              <Dialog open={isDeleteDialogOpen} onOpenChange={setIsDeleteDialogOpen}>
                <DialogTrigger asChild>
                  <Button variant="destructive" className="w-full">Delete Template</Button>
                </DialogTrigger>
                <DialogContent>
                  <DialogHeader>
                    <DialogTitle>Delete Template</DialogTitle>
                    <DialogDescription>
                      Are you sure you want to delete this template? This action cannot be undone.
                    </DialogDescription>
                  </DialogHeader>
                  <DialogFooter>
                    <Button variant="outline" onClick={() => setIsDeleteDialogOpen(false)}>Cancel</Button>
                    <Button variant="destructive" onClick={handleDeleteTemplate}>Delete</Button>
                  </DialogFooter>
                </DialogContent>
              </Dialog>
            )}
          </TabsContent>
          
          <TabsContent value="variables" className="space-y-4 mt-4">
            <Card>
              <CardHeader>
                <CardTitle>Template Variables</CardTitle>
                <CardDescription>
                  Variables detected in your prompt template
                </CardDescription>
              </CardHeader>
              <CardContent>
                {isEditing ? (
                  <div className="space-y-4">
                    <p className="text-sm text-muted-foreground">
                      The following variables were detected in your template. Configure their properties below.
                    </p>
                    
                    {templateVariables.length === 0 ? (
                      <div className="rounded-md bg-muted p-4 text-center">
                        <p>No variables detected in the template.</p>
                        <p className="text-sm text-muted-foreground mt-1">
                          Add variables using double curly braces, e.g., {{'{{'}}variable_name{{'}}'}}
                        </p>
                      </div>
                    ) : (
                      <div className="space-y-6">
                        {templateVariables.map((varName) => {
                          const existingVar = template.variables.find(v => v.name === varName)
                          
                          return (
                            <div key={varName} className="rounded-md border p-4">
                              <div className="font-medium">{varName}</div>
                              <div className="grid gap-4 mt-2">
                                <div className="grid gap-2">
                                  <Label htmlFor={`var-desc-${varName}`}>Description</Label>
                                  <Input 
                                    id={`var-desc-${varName}`} 
                                    defaultValue={existingVar?.description || ""}
                                    placeholder="Describe this variable"
                                  />
                                </div>
                                
                                <div className="grid grid-cols-2 gap-4">
                                  <div className="grid gap-2">
                                    <Label htmlFor={`var-type-${varName}`}>Type</Label>
                                    <Select defaultValue={existingVar?.type || "string"}>
                                      <SelectTrigger id={`var-type-${varName}`}>
                                        <SelectValue placeholder="Select type" />
                                      </SelectTrigger>
                                      <SelectContent>
                                        <SelectItem value="string">String</SelectItem>
                                        <SelectItem value="number">Number</SelectItem>
                                        <SelectItem value="boolean">Boolean</SelectItem>
                                        <SelectItem value="array">Array</SelectItem>
                                        <SelectItem value="object">Object</SelectItem>
                                      </SelectContent>
                                    </Select>
                                  </div>
                                  
                                  <div className="flex items-center gap-2">
                                    <div className="grid gap-2 flex-1">
                                      <Label htmlFor={`var-required-${varName}`}>Required</Label>
                                      <div className="flex items-center gap-2">
                                        <Switch 
                                          id={`var-required-${varName}`} 
                                          defaultChecked={existingVar?.required !== false}
                                        />
                                        <Label htmlFor={`var-required-${varName}`}>
                                          {existingVar?.required !== false ? "Yes" : "No"}
                                        </Label>
                                      </div>
                                    </div>
                                  </div>
                                </div>
                                
                                <div className="grid gap-2">
                                  <Label htmlFor={`var-default-${varName}`}>Default Value</Label>
                                  <Input 
                                    id={`var-default-${varName}`} 
                                    defaultValue={existingVar?.defaultValue || ""}
                                    placeholder="Default value (optional)"
                                  />
                                </div>
                                
                                <div className="grid gap-2">
                                  <Label htmlFor={`var-options-${varName}`}>Options (comma-separated)</Label>
                                  <Input 
                                    id={`var-options-${varName}`} 
                                    defaultValue={existingVar?.options?.join(", ") || ""}
                                    placeholder="Option1, Option2, Option3"
                                  />
                                  <p className="text-xs text-muted-foreground">
                                    Leave empty if this variable doesn't have predefined options
                                  </p>
                                </div>
                              </div>
                            </div>
                          )
                        })}
                      </div>
                    )}
                  </div>
                ) : (
                  <div className="space-y-4">
                    {template.variables.length === 0 ? (
                      <div className="rounded-md bg-muted p-4 text-center">
                        <p>No variables defined for this template.</p>
                      </div>
                    ) : (
                      <div className="space-y-4">
                        {template.variables.map((variable) => (
                          <div key={variable.name} className="rounded-md border p-4">
                            <div className="flex items-center justify-between">
                              <div className="font-medium">
                                {variable.name}
                                {variable.required && <span className="text-red-500 ml-1">*</span>}
                              </div>
                              <Badge variant="outline">{variable.type}</Badge>
                            </div>
                            <p className="text-sm text-muted-foreground mt-1">
                              {variable.description}
                            </p>
                            
                            {variable.defaultValue && (
                              <div className="mt-2">
                                <span className="text-xs font-medium">Default:</span>{" "}
                                <span className="text-xs text-muted-foreground">
                                  {variable.defaultValue}
                                </span>
                              </div>
                            )}
                            
                            {variable.options && variable.options.length > 0 && (
                              <div className="mt-2">
                                <span className="text-xs font-medium">Options:</span>{" "}
                                <span className="text-xs text-muted-foreground">
                                  {variable.options.join(", ")}
                                </span>
                              </div>
                            )}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </CardContent>
            </Card>
          </TabsContent>
          
          <TabsContent value="usage" className="space-y-4 mt-4">
            <Card>
              <CardHeader>
                <CardTitle>Usage Statistics</CardTitle>
                <CardDescription>
                  How this template is being used
                </CardDescription>
              </CardHeader>
              <CardContent>
                <div className="space-y-4">
                  <div className="grid grid-cols-3 gap-4">
                    <div className="rounded-md border p-4 text-center">
                      <div className="text-2xl font-bold">{template.usageCount}</div>
                      <div className="text-sm text-muted-foreground">Total Uses</div>
                    </div>
                    <div className="rounded-md border p-4 text-center">
                      <div className="text-2xl font-bold">
                        {Math.floor(template.usageCount / 30)}
                      </div>
                      <div className="text-sm text-muted-foreground">Uses / Day</div>
                    </div>
                    <div className="rounded-md border p-4 text-center">
                      <div className="text-2xl font-bold">
                        {template.modelId ? 
                          mockModels.find(m => m.id === template.modelId)?.name || "Custom" : 
                          "Various"}
                      </div>
                      <div className="text-sm text-muted-foreground">Primary Model</div>
                    </div>
                  </div>
                  
                  <div className="rounded-md border p-4">
                    <h3 className="font-medium">Usage Over Time</h3>
                    <div className="h-[200px] flex items-center justify-center">
                      <p className="text-muted-foreground">Usage chart will appear here</p>
                    </div>
                  </div>
                  
                  <div className="rounded-md border p-4">
                    <h3 className="font-medium">Top Users</h3>
                    <div className="mt-2">
                      <p className="text-muted-foreground">No user data available</p>
                    </div>
                  </div>
                </div>
              </CardContent>
            </Card>
          </TabsContent>
        </Tabs>
      </div>
    </DashboardLayout>
  )
}
