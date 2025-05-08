"use client"

import { useState, useEffect } from "react"
import { DashboardLayout } from "@/components/layout/dashboard-layout"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Separator } from "@/components/ui/separator"
import { AlertCircle, CheckCircle2 } from "lucide-react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"

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
  const [models, setModels] = useState<Model[]>([])
  const [rules, setRules] = useState<RoutingRule[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const fetchModels = async () => {
      try {
        const response = await fetch('/api/ai/models')
        if (!response.ok) {
          throw new Error('Failed to fetch models')
        }
        const data = await response.json()
        setModels(data.models || [])
      } catch (err) {
        setError('Error loading models: ' + (err instanceof Error ? err.message : String(err)))
      }
    }

    const fetchRules = async () => {
      try {
        const response = await fetch('/api/ai/routing/rules')
        if (!response.ok) {
          // This is expected to fail if the endpoint is not implemented yet
          console.warn('Routing rules endpoint not available')
          return
        }
        const data = await response.json()
        setRules(data.rules || [])
      } catch (err) {
        console.warn('Error loading routing rules:', err)
      } finally {
        setLoading(false)
      }
    }

    fetchModels().then(() => fetchRules())
  }, [])

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
          <Button>Add Custom Model</Button>
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
            <TabsTrigger value="models">Available Models</TabsTrigger>
            <TabsTrigger value="routing">Routing Rules</TabsTrigger>
          </TabsList>
          
          <TabsContent value="models">
            <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
              {loading ? (
                <p>Loading models...</p>
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
                  <Button size="sm">Add Rule</Button>
                </div>
                <CardDescription>
                  Rules for routing requests to specific models based on content
                </CardDescription>
              </CardHeader>
              <CardContent>
                {rules.length > 0 ? (
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
                    <Button className="mt-4">Create First Rule</Button>
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
