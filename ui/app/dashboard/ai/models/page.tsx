"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import { DashboardLayout } from "@/components/layout/dashboard-layout"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Switch } from "@/components/ui/switch"
import { Badge } from "@/components/ui/badge"
import { 
  PlusCircle, 
  Pencil, 
  Trash2, 
  RefreshCw, 
  Filter, 
  Search,
  Zap,
  Cpu,
  Server,
  Brain
} from "lucide-react"
import { useToast } from "@/components/ui/use-toast"
import { DataTable } from "@/components/ui/data-table"
import { useApiData, useApiMutation } from "@/lib/hooks/use-api-data"
import { aiModelsApi } from "@/lib/api-client"
import { AIModel } from "@/lib/api-client/ai-models"
import { ColumnDef } from "@tanstack/react-table"

export default function AIModelsPage() {
  const router = useRouter()
  const { toast } = useToast()
  const [activeTab, setActiveTab] = useState("all")
  const [providerFilter, setProviderFilter] = useState<string>("all")
  const [searchQuery, setSearchQuery] = useState("")

  // Fetch AI models data
  const { 
    data: modelsData, 
    isLoading, 
    error, 
    refetch,
    isRefetching
  } = useApiData(
    () => aiModelsApi.getModels(),
    {
      onError: (err) => {
        toast({
          title: "Error fetching AI models",
          description: err.message,
          variant: "destructive"
        })
      }
    }
  )

  // Enable/disable model mutation
  const { 
    mutate: toggleModelStatus 
  } = useApiMutation(
    async ({ model, enabled }: { model: AIModel, enabled: boolean }) => {
      if (enabled) {
        return aiModelsApi.enableModel(model.id)
      } else {
        return aiModelsApi.disableModel(model.id)
      }
    },
    {
      onSuccess: (data, { model, enabled }) => {
        toast({
          title: `Model ${enabled ? 'enabled' : 'disabled'}`,
          description: `${model.name} has been ${enabled ? 'enabled' : 'disabled'}`
        })
        refetch()
      },
      onError: (error, { model, enabled }) => {
        toast({
          title: "Error",
          description: `Failed to ${enabled ? 'enable' : 'disable'} model: ${error.message}`,
          variant: "destructive"
        })
      }
    }
  )

  // Delete model mutation
  const { 
    mutate: deleteModel 
  } = useApiMutation(
    async (model: AIModel) => {
      return aiModelsApi.deleteModel(model.id)
    },
    {
      onSuccess: (data, model) => {
        toast({
          title: "Model deleted",
          description: `${model.name} has been deleted`
        })
        refetch()
      },
      onError: (error, model) => {
        toast({
          title: "Error",
          description: `Failed to delete model: ${error.message}`,
          variant: "destructive"
        })
      }
    }
  )

  // Handle model status toggle
  const handleStatusToggle = (model: AIModel, enabled: boolean) => {
    toggleModelStatus({ model, enabled })
  }

  // Handle model deletion
  const handleDelete = (model: AIModel) => {
    if (window.confirm(`Are you sure you want to delete the model ${model.name}?`)) {
      deleteModel(model)
    }
  }

  // Get unique providers for filtering
  const providers = modelsData?.models 
    ? Array.from(new Set(modelsData.models.map(model => model.provider)))
    : []

  // Filter models based on active tab, provider filter, and search query
  const models = modelsData?.models || []
  const filteredModels = models
    .filter(model => activeTab === "all" || (
      activeTab === "enabled" ? model.enabled : !model.enabled
    ))
    .filter(model => providerFilter === "all" || model.provider === providerFilter)
    .filter(model => {
      if (!searchQuery) return true
      const query = searchQuery.toLowerCase()
      return (
        model.id.toLowerCase().includes(query) ||
        model.name.toLowerCase().includes(query) ||
        model.provider.toLowerCase().includes(query) ||
        (model.description || "").toLowerCase().includes(query)
      )
    })

  // Define table columns
  const columns: ColumnDef<AIModel>[] = [
    {
      accessorKey: "name",
      header: "Name",
      cell: ({ row }) => <span className="font-medium">{row.original.name}</span>
    },
    {
      accessorKey: "provider",
      header: "Provider",
      cell: ({ row }) => {
        const provider = row.original.provider
        let color = "gray"
        
        if (provider === "OpenAI") color = "green"
        else if (provider === "Anthropic") color = "blue"
        else if (provider === "Google") color = "yellow"
        else if (provider === "Cohere") color = "purple"
        
        return (
          <Badge variant="outline" className={`bg-${color}-100 text-${color}-800 dark:bg-${color}-900 dark:text-${color}-300 border-${color}-200`}>
            {provider}
          </Badge>
        )
      }
    },
    {
      accessorKey: "maxTokens",
      header: "Max Tokens",
      cell: ({ row }) => row.original.maxTokens?.toLocaleString() || "N/A"
    },
    {
      accessorKey: "costPerToken",
      header: "Cost/Token",
      cell: ({ row }) => {
        const cost = row.original.costPerToken
        return cost ? `$${cost.toFixed(6)}` : "N/A"
      }
    },
    {
      accessorKey: "status",
      header: "Status",
      cell: ({ row }) => {
        const model = row.original
        return (
          <div className="flex items-center space-x-2">
            <Switch
              checked={model.enabled}
              onCheckedChange={(checked) => handleStatusToggle(model, checked)}
              id={`model-status-${model.id}`}
            />
            <Badge variant={model.enabled ? "default" : "secondary"}>
              {model.enabled ? "Enabled" : "Disabled"}
            </Badge>
          </div>
        )
      }
    },
    {
      id: "actions",
      header: "Actions",
      cell: ({ row }) => {
        const model = row.original
        return (
          <div className="flex justify-end space-x-1">
            <Button variant="ghost" size="icon" onClick={() => router.push(`/dashboard/ai/models/${model.id}/edit`)}>
              <Pencil className="h-4 w-4" />
              <span className="sr-only">Edit</span>
            </Button>
            <Button variant="ghost" size="icon" onClick={() => handleDelete(model)}>
              <Trash2 className="h-4 w-4" />
              <span className="sr-only">Delete</span>
            </Button>
          </div>
        )
      }
    }
  ]

  return (
    <DashboardLayout>
      <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold">AI Models</h1>
            <p className="text-muted-foreground">
              Manage AI models for your gateway
            </p>
          </div>
          <div className="flex gap-2">
            <Button 
              variant="outline" 
              size="icon" 
              onClick={() => refetch()}
              disabled={isRefetching}
            >
              <RefreshCw className={`h-4 w-4 ${isRefetching ? 'animate-spin' : ''}`} />
            </Button>
            <Button onClick={() => router.push('/dashboard/ai/models/create')}>
              <PlusCircle className="mr-2 h-4 w-4" />
              Add Model
            </Button>
          </div>
        </div>

        <Card className="mt-6">
          <CardHeader>
            <CardTitle>AI Models</CardTitle>
            <CardDescription>
              Configure and manage AI models for your gateway
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              <div className="flex items-center space-x-2">
                <div className="relative flex-1 max-w-sm">
                  <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
                  <Input
                    placeholder="Search models..."
                    className="pl-8"
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                  />
                </div>
                <Select
                  defaultValue="all"
                  value={providerFilter}
                  onValueChange={(value) => setProviderFilter(value)}
                >
                  <SelectTrigger className="w-[180px]">
                    <Filter className="mr-2 h-4 w-4" />
                    <SelectValue placeholder="Provider" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">All Providers</SelectItem>
                    {providers.map(provider => (
                      <SelectItem key={provider} value={provider}>{provider}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <Tabs defaultValue="all" className="w-full" onValueChange={setActiveTab}>
                <TabsList>
                  <TabsTrigger value="all" className="flex items-center gap-2">
                    <Brain className="h-4 w-4" />
                    All Models
                  </TabsTrigger>
                  <TabsTrigger value="enabled" className="flex items-center gap-2">
                    <Zap className="h-4 w-4" />
                    Enabled
                  </TabsTrigger>
                  <TabsTrigger value="disabled" className="flex items-center gap-2">
                    <Server className="h-4 w-4" />
                    Disabled
                  </TabsTrigger>
                </TabsList>

                <TabsContent value="all" className="mt-4">
                  {isLoading ? (
                    <div className="flex justify-center py-8">
                      <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
                    </div>
                  ) : filteredModels.length === 0 ? (
                    <div className="text-center py-8 text-muted-foreground">
                      No models found. {searchQuery && "Try adjusting your search."}
                    </div>
                  ) : (
                    <DataTable
                      columns={columns}
                      data={filteredModels}
                      searchColumn="name"
                      searchPlaceholder="Filter by name..."
                    />
                  )}
                </TabsContent>
                
                {['enabled', 'disabled'].map(tabValue => (
                  <TabsContent key={tabValue} value={tabValue} className="mt-4">
                    {isLoading ? (
                      <div className="flex justify-center py-8">
                        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
                      </div>
                    ) : filteredModels.length === 0 ? (
                      <div className="text-center py-8 text-muted-foreground">
                        No {tabValue} models found.
                      </div>
                    ) : (
                      <DataTable
                        columns={columns}
                        data={filteredModels}
                        searchColumn="name"
                        searchPlaceholder="Filter by name..."
                      />
                    )}
                  </TabsContent>
                ))}
              </Tabs>
            </div>
          </CardContent>
        </Card>
      </div>
    </DashboardLayout>
  )
}
