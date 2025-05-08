"use client"

import { DashboardLayout } from "@/components/layout/dashboard-layout"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Separator } from "@/components/ui/separator"
import { AlertCircle, CheckCircle2, Cpu, GitFork, Plus, RefreshCw } from "lucide-react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { useApiData } from "@/lib/hooks/use-api-data"
import { aiApi } from "@/lib/api-client"
import { useToast } from "@/components/ui/use-toast"

interface Model {
  id: string
  name: string
  provider: string
}

interface RoutingRule {
  id: string
  name: string
  priority: number
  condition: {
    type: string
    pattern: string
    contentTypes: string[]
    requestTypes: string[]
  }
  targetModel: string
}

export default function ModelsPage() {
  const { toast } = useToast()

  const {
    data: modelsData,
    isLoading: isLoadingModels,
    error: modelsError,
    refetch: refetchModels,
    isRefetching: isRefetchingModels
  } = useApiData(
    () => aiApi.getModels(),
    {
      onError: (err) => {
        toast({
          variant: "destructive",
          title: "Error loading models",
          description: err.message,
        })
      }
    }
  )

  const {
    data: rulesData,
    isLoading: isLoadingRules,
    error: rulesError,
    refetch: refetchRules,
    isRefetching: isRefetchingRules
  } = useApiData(
    () => aiApi.getRoutingRules(),
    {
      onError: (err) => {
        console.warn('Error loading routing rules:', err)
      }
    }
  )

  const models = modelsData?.models || []
  const rules = rulesData?.rules || []
  const isLoading = isLoadingModels || isLoadingRules
  const isRefetching = isRefetchingModels || isRefetchingRules
  const error = modelsError?.message || null

  const getConditionDescription = (condition: RoutingRule['condition']) => {
    switch (condition.type) {
      case 'CONTAINS':
        return `Content contains "${condition.pattern}"`
      case 'STARTS_WITH':
        return `Content starts with "${condition.pattern}"`
      case 'ENDS_WITH':
        return `Content ends with "${condition.pattern}"`
      case 'REGEX':
        return `Content matches regex "${condition.pattern}"`
      case 'TOKEN_COUNT':
        return `Token count ≥ ${condition.pattern}`
      case 'LANGUAGE':
        return `Language is ${condition.pattern}`
      default:
        return `${condition.type}: ${condition.pattern}`
    }
  }

  return (
    <DashboardLayout>
      <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold">AI Models</h1>
            <p className="text-muted-foreground">
              Manage AI models and routing rules
            </p>
          </div>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="icon"
              onClick={() => {
                refetchModels()
                refetchRules()
                toast({
                  title: "Refreshed",
                  description: "Model data has been refreshed",
                })
              }}
              disabled={isRefetching}
            >
              <RefreshCw className={`h-4 w-4 ${isRefetching ? 'animate-spin' : ''}`} />
            </Button>
            <Button>
              <Plus className="h-4 w-4 mr-2" />
              Add Custom Model
            </Button>
          </div>
        </div>

        {error && (
          <Alert variant="destructive">
            <AlertCircle className="h-4 w-4" />
            <AlertTitle>Error</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        <Tabs defaultValue="models">
          <TabsList>
            <TabsTrigger value="models" className="flex items-center gap-2">
              <Cpu className="h-4 w-4" />
              Available Models
            </TabsTrigger>
            <TabsTrigger value="routing" className="flex items-center gap-2">
              <GitFork className="h-4 w-4" />
              Routing Rules
            </TabsTrigger>
          </TabsList>

          <TabsContent value="models">
            <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
              {isLoading ? (
                Array(6).fill(0).map((_, i) => (
                  <Card key={i} className="opacity-70">
                    <CardHeader>
                      <div className="flex items-center justify-between">
                        <div className="h-6 w-24 bg-muted rounded animate-pulse"></div>
                        <div className="h-5 w-16 bg-muted rounded animate-pulse"></div>
                      </div>
                      <div className="h-4 w-32 bg-muted rounded animate-pulse mt-2"></div>
                    </CardHeader>
                    <CardContent>
                      <div className="flex items-center gap-2">
                        <div className="h-4 w-4 bg-muted rounded-full animate-pulse"></div>
                        <div className="h-4 w-16 bg-muted rounded animate-pulse"></div>
                      </div>
                      <Separator className="my-4" />
                      <div className="flex justify-end">
                        <div className="h-8 w-24 bg-muted rounded animate-pulse"></div>
                      </div>
                    </CardContent>
                  </Card>
                ))
              ) : (
                models.map((model) => (
                  <Card key={model.id}>
                    <CardHeader>
                      <div className="flex items-center justify-between">
                        <CardTitle>{model.name}</CardTitle>
                        <Badge>{model.provider}</Badge>
                      </div>
                      <CardDescription>ID: {model.id}</CardDescription>
                    </CardHeader>
                    <CardContent>
                      <div className="flex items-center gap-2">
                        <CheckCircle2 className="h-4 w-4 text-green-500" />
                        <span className="text-sm">Available</span>
                      </div>
                      <Separator className="my-4" />
                      <div className="flex justify-end">
                        <Button variant="outline" size="sm">View Details</Button>
                      </div>
                    </CardContent>
                  </Card>
                ))
              )}
            </div>
          </TabsContent>

          <TabsContent value="routing">
            <Card>
              <CardHeader>
                <div className="flex items-center justify-between">
                  <CardTitle>Model Routing Rules</CardTitle>
                  <Button size="sm">
                    <Plus className="h-4 w-4 mr-2" />
                    Add Rule
                  </Button>
                </div>
                <CardDescription>
                  Rules for routing requests to specific models based on content
                </CardDescription>
              </CardHeader>
              <CardContent>
                {isLoading ? (
                  <div className="space-y-4">
                    {Array(3).fill(0).map((_, i) => (
                      <Card key={i} className="opacity-70">
                        <CardHeader>
                          <div className="flex items-center justify-between">
                            <div className="h-5 w-32 bg-muted rounded animate-pulse"></div>
                            <div className="h-5 w-24 bg-muted rounded animate-pulse"></div>
                          </div>
                        </CardHeader>
                        <CardContent>
                          <div className="grid grid-cols-2 gap-4">
                            <div>
                              <div className="h-4 w-16 bg-muted rounded animate-pulse mb-2"></div>
                              <div className="h-4 w-48 bg-muted rounded animate-pulse"></div>
                            </div>
                            <div>
                              <div className="h-4 w-20 bg-muted rounded animate-pulse mb-2"></div>
                              <div className="h-4 w-32 bg-muted rounded animate-pulse"></div>
                            </div>
                          </div>
                          <div className="mt-4 flex justify-end gap-2">
                            <div className="h-8 w-16 bg-muted rounded animate-pulse"></div>
                            <div className="h-8 w-16 bg-muted rounded animate-pulse"></div>
                          </div>
                        </CardContent>
                      </Card>
                    ))}
                  </div>
                ) : rules.length > 0 ? (
                  <div className="space-y-4">
                    {rules.map((rule) => (
                      <Card key={rule.id}>
                        <CardHeader>
                          <div className="flex items-center justify-between">
                            <CardTitle className="text-base">{rule.name}</CardTitle>
                            <Badge>Priority: {rule.priority}</Badge>
                          </div>
                        </CardHeader>
                        <CardContent>
                          <div className="grid grid-cols-2 gap-4">
                            <div>
                              <p className="text-sm font-medium">Condition</p>
                              <p className="text-sm text-muted-foreground">
                                {getConditionDescription(rule.condition)}
                              </p>
                            </div>
                            <div>
                              <p className="text-sm font-medium">Target Model</p>
                              <p className="text-sm text-muted-foreground">{rule.targetModel}</p>
                            </div>
                          </div>
                          <div className="mt-4 flex justify-end gap-2">
                            <Button variant="outline" size="sm">Edit</Button>
                            <Button variant="destructive" size="sm">Delete</Button>
                          </div>
                        </CardContent>
                      </Card>
                    ))}
                  </div>
                ) : (
                  <div className="flex flex-col items-center justify-center py-8">
                    <p className="text-muted-foreground">No routing rules defined</p>
                    <Button className="mt-4">
                      <Plus className="h-4 w-4 mr-2" />
                      Create First Rule
                    </Button>
                  </div>
                )}
              </CardContent>
            </Card>
          </TabsContent>
        </Tabs>
      </div>
    </DashboardLayout>
  )
}
