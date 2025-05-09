"use client"

import { useState } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Badge } from "@/components/ui/badge"
import { 
  Search, 
  Code, 
  FileText, 
  Server, 
  Copy, 
  ExternalLink,
  ChevronDown,
  ChevronRight
} from "lucide-react"
import { cn } from "@/lib/utils"

interface ApiEndpoint {
  id: string
  path: string
  method: string
  summary: string
  description?: string
  tags?: string[]
  parameters?: ApiParameter[]
  requestBody?: ApiRequestBody
  responses?: Record<string, ApiResponse>
  security?: ApiSecurity[]
  deprecated?: boolean
}

interface ApiParameter {
  name: string
  in: "path" | "query" | "header" | "cookie"
  description?: string
  required?: boolean
  schema?: ApiSchema
  example?: any
}

interface ApiRequestBody {
  description?: string
  required?: boolean
  content: Record<string, { schema: ApiSchema }>
}

interface ApiResponse {
  description: string
  content?: Record<string, { schema: ApiSchema }>
}

interface ApiSchema {
  type?: string
  format?: string
  properties?: Record<string, ApiSchema>
  items?: ApiSchema
  required?: string[]
  enum?: any[]
  example?: any
  oneOf?: ApiSchema[]
  anyOf?: ApiSchema[]
  allOf?: ApiSchema[]
  $ref?: string
}

interface ApiSecurity {
  [name: string]: string[]
}

interface ApiDocumentationProps {
  endpoints: ApiEndpoint[]
  title?: string
  description?: string
  version?: string
}

export function ApiDocumentation({
  endpoints,
  title = "API Documentation",
  description = "Explore and test the API endpoints",
  version = "1.0.0"
}: ApiDocumentationProps) {
  const [searchQuery, setSearchQuery] = useState("")
  const [selectedTags, setSelectedTags] = useState<string[]>([])
  
  // Get all unique tags
  const allTags = Array.from(
    new Set(
      endpoints.flatMap(endpoint => endpoint.tags || [])
    )
  ).sort()
  
  // Filter endpoints based on search query and selected tags
  const filteredEndpoints = endpoints.filter(endpoint => {
    // Filter by search query
    const matchesSearch = 
      searchQuery === "" ||
      endpoint.path.toLowerCase().includes(searchQuery.toLowerCase()) ||
      endpoint.summary.toLowerCase().includes(searchQuery.toLowerCase()) ||
      endpoint.description?.toLowerCase().includes(searchQuery.toLowerCase())
    
    // Filter by selected tags
    const matchesTags = 
      selectedTags.length === 0 ||
      selectedTags.some(tag => endpoint.tags?.includes(tag))
    
    return matchesSearch && matchesTags
  })
  
  // Toggle tag selection
  const toggleTag = (tag: string) => {
    setSelectedTags(prev => 
      prev.includes(tag)
        ? prev.filter(t => t !== tag)
        : [...prev, tag]
    )
  }
  
  // Get method badge color
  const getMethodColor = (method: string) => {
    switch (method.toUpperCase()) {
      case "GET":
        return "bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-300 border-blue-200"
      case "POST":
        return "bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-300 border-green-200"
      case "PUT":
        return "bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-300 border-yellow-200"
      case "DELETE":
        return "bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-300 border-red-200"
      case "PATCH":
        return "bg-purple-100 text-purple-800 dark:bg-purple-900 dark:text-purple-300 border-purple-200"
      default:
        return "bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-300 border-gray-200"
    }
  }
  
  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-2">
        <h1 className="text-3xl font-bold">{title}</h1>
        <div className="flex items-center gap-2">
          <Badge variant="outline">v{version}</Badge>
          <p className="text-muted-foreground">{description}</p>
        </div>
      </div>
      
      <div className="flex flex-col gap-4 md:flex-row md:items-start">
        {/* Sidebar */}
        <div className="w-full md:w-64 space-y-4">
          <div className="relative">
            <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              placeholder="Search endpoints..."
              className="pl-8"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>
          
          <Card>
            <CardHeader className="py-3">
              <CardTitle className="text-sm font-medium">Tags</CardTitle>
            </CardHeader>
            <CardContent className="py-0">
              <div className="space-y-1">
                {allTags.map(tag => (
                  <Button
                    key={tag}
                    variant="ghost"
                    size="sm"
                    className={cn(
                      "justify-start w-full",
                      selectedTags.includes(tag) && "bg-accent text-accent-foreground"
                    )}
                    onClick={() => toggleTag(tag)}
                  >
                    {tag}
                  </Button>
                ))}
              </div>
            </CardContent>
          </Card>
        </div>
        
        {/* Main content */}
        <div className="flex-1 space-y-4">
          <Tabs defaultValue="endpoints">
            <TabsList>
              <TabsTrigger value="endpoints" className="flex items-center gap-2">
                <Server className="h-4 w-4" />
                Endpoints
              </TabsTrigger>
              <TabsTrigger value="schemas" className="flex items-center gap-2">
                <Code className="h-4 w-4" />
                Schemas
              </TabsTrigger>
              <TabsTrigger value="docs" className="flex items-center gap-2">
                <FileText className="h-4 w-4" />
                Documentation
              </TabsTrigger>
            </TabsList>
            
            <TabsContent value="endpoints" className="space-y-4 pt-4">
              {filteredEndpoints.length === 0 ? (
                <div className="text-center py-8">
                  <p className="text-muted-foreground">No endpoints found matching your criteria</p>
                </div>
              ) : (
                filteredEndpoints.map(endpoint => (
                  <EndpointCard key={endpoint.id} endpoint={endpoint} />
                ))
              )}
            </TabsContent>
            
            <TabsContent value="schemas" className="pt-4">
              <Card>
                <CardHeader>
                  <CardTitle>API Schemas</CardTitle>
                  <CardDescription>
                    Data models used in the API
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  <p className="text-center py-8">
                    Schema documentation coming soon
                  </p>
                </CardContent>
              </Card>
            </TabsContent>
            
            <TabsContent value="docs" className="pt-4">
              <Card>
                <CardHeader>
                  <CardTitle>API Documentation</CardTitle>
                  <CardDescription>
                    Detailed documentation and guides
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  <p className="text-center py-8">
                    Detailed documentation coming soon
                  </p>
                </CardContent>
              </Card>
            </TabsContent>
          </Tabs>
        </div>
      </div>
    </div>
  )
}

interface EndpointCardProps {
  endpoint: ApiEndpoint
}

function EndpointCard({ endpoint }: EndpointCardProps) {
  const [isExpanded, setIsExpanded] = useState(false)
  
  return (
    <Card className={cn(
      endpoint.deprecated && "opacity-60"
    )}>
      <CardHeader className="pb-2">
        <div className="flex items-start justify-between">
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <Badge 
                variant="outline" 
                className={cn(
                  getMethodColor(endpoint.method),
                  "font-mono text-xs"
                )}
              >
                {endpoint.method.toUpperCase()}
              </Badge>
              <CardTitle className="font-mono text-base">
                {endpoint.path}
              </CardTitle>
              {endpoint.deprecated && (
                <Badge variant="destructive">Deprecated</Badge>
              )}
            </div>
            <CardDescription>
              {endpoint.summary}
            </CardDescription>
          </div>
          <Button
            variant="ghost"
            size="icon"
            onClick={() => setIsExpanded(!isExpanded)}
            aria-label={isExpanded ? "Collapse" : "Expand"}
          >
            {isExpanded ? (
              <ChevronDown className="h-4 w-4" />
            ) : (
              <ChevronRight className="h-4 w-4" />
            )}
          </Button>
        </div>
        {endpoint.tags && endpoint.tags.length > 0 && (
          <div className="flex flex-wrap gap-1 mt-1">
            {endpoint.tags.map(tag => (
              <Badge key={tag} variant="secondary" className="text-xs">
                {tag}
              </Badge>
            ))}
          </div>
        )}
      </CardHeader>
      
      {isExpanded && (
        <CardContent className="pb-4">
          {endpoint.description && (
            <div className="mb-4">
              <h4 className="text-sm font-medium mb-1">Description</h4>
              <p className="text-sm text-muted-foreground">{endpoint.description}</p>
            </div>
          )}
          
          {endpoint.parameters && endpoint.parameters.length > 0 && (
            <div className="mb-4">
              <h4 className="text-sm font-medium mb-2">Parameters</h4>
              <div className="border rounded-md">
                <div className="grid grid-cols-4 gap-4 p-3 border-b bg-muted/50 text-xs font-medium">
                  <div>Name</div>
                  <div>Location</div>
                  <div>Type</div>
                  <div>Description</div>
                </div>
                {endpoint.parameters.map((param, index) => (
                  <div 
                    key={param.name} 
                    className={cn(
                      "grid grid-cols-4 gap-4 p-3 text-xs",
                      index !== endpoint.parameters!.length - 1 && "border-b"
                    )}
                  >
                    <div className="font-medium">
                      {param.name}
                      {param.required && <span className="text-red-500 ml-1">*</span>}
                    </div>
                    <div>{param.in}</div>
                    <div>{param.schema?.type || "any"}</div>
                    <div className="text-muted-foreground">{param.description || "-"}</div>
                  </div>
                ))}
              </div>
            </div>
          )}
          
          {endpoint.requestBody && (
            <div className="mb-4">
              <h4 className="text-sm font-medium mb-2">Request Body</h4>
              <div className="border rounded-md p-3 space-y-2">
                {endpoint.requestBody.description && (
                  <p className="text-xs text-muted-foreground">{endpoint.requestBody.description}</p>
                )}
                {Object.entries(endpoint.requestBody.content).map(([contentType, { schema }]) => (
                  <div key={contentType} className="space-y-2">
                    <div className="flex items-center justify-between">
                      <Badge variant="outline" className="font-mono text-xs">
                        {contentType}
                      </Badge>
                      <Button variant="ghost" size="icon" className="h-6 w-6">
                        <Copy className="h-3 w-3" />
                      </Button>
                    </div>
                    <pre className="bg-muted p-2 rounded-md text-xs overflow-auto max-h-60">
                      {JSON.stringify(schema.example || {}, null, 2)}
                    </pre>
                  </div>
                ))}
              </div>
            </div>
          )}
          
          {endpoint.responses && Object.keys(endpoint.responses).length > 0 && (
            <div>
              <h4 className="text-sm font-medium mb-2">Responses</h4>
              <div className="space-y-2">
                {Object.entries(endpoint.responses).map(([statusCode, response]) => (
                  <div key={statusCode} className="border rounded-md overflow-hidden">
                    <div className="bg-muted/50 p-3 flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <Badge 
                          variant="outline" 
                          className={cn(
                            statusCode.startsWith("2") ? "bg-green-100 text-green-800" :
                            statusCode.startsWith("4") ? "bg-yellow-100 text-yellow-800" :
                            statusCode.startsWith("5") ? "bg-red-100 text-red-800" :
                            "bg-blue-100 text-blue-800",
                            "font-mono text-xs"
                          )}
                        >
                          {statusCode}
                        </Badge>
                        <span className="text-xs font-medium">{response.description}</span>
                      </div>
                      {response.content && (
                        <Button variant="ghost" size="icon" className="h-6 w-6">
                          <Copy className="h-3 w-3" />
                        </Button>
                      )}
                    </div>
                    {response.content && Object.entries(response.content).map(([contentType, { schema }]) => (
                      <div key={contentType} className="p-3 space-y-2">
                        <Badge variant="outline" className="font-mono text-xs">
                          {contentType}
                        </Badge>
                        <pre className="bg-muted p-2 rounded-md text-xs overflow-auto max-h-60">
                          {JSON.stringify(schema.example || {}, null, 2)}
                        </pre>
                      </div>
                    ))}
                  </div>
                ))}
              </div>
            </div>
          )}
          
          <div className="flex justify-end mt-4">
            <Button variant="outline" size="sm" className="flex items-center gap-1">
              <ExternalLink className="h-3 w-3" />
              Try it
            </Button>
          </div>
        </CardContent>
      )}
    </Card>
  )
}
