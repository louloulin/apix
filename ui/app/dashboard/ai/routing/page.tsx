"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import { DashboardLayout } from "@/components/layout/dashboard-layout"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Switch } from "@/components/ui/switch"
import { Badge } from "@/components/ui/badge"
import { 
  PlusCircle, 
  Pencil, 
  Trash2, 
  RefreshCw, 
  Search,
  ArrowUpDown,
  Filter
} from "lucide-react"
import { useToast } from "@/components/ui/use-toast"
import { DataTable } from "@/components/ui/data-table"
import { useApiData, useApiMutation } from "@/lib/hooks/use-api-data"
import { aiModelsApi } from "@/lib/api-client"
import { AIRoutingRule } from "@/lib/api-client/ai-models"
import { ColumnDef } from "@tanstack/react-table"

export default function AIRoutingPage() {
  const router = useRouter()
  const { toast } = useToast()
  const [searchQuery, setSearchQuery] = useState("")

  // Fetch routing rules data
  const { 
    data: rulesData, 
    isLoading, 
    error, 
    refetch,
    isRefetching
  } = useApiData(
    () => aiModelsApi.getRoutingRules(),
    {
      onError: (err) => {
        toast({
          title: "Error fetching routing rules",
          description: err.message,
          variant: "destructive"
        })
      }
    }
  )

  // Fetch models data for displaying model names
  const { 
    data: modelsData
  } = useApiData(
    () => aiModelsApi.getModels(),
    {
      onError: (err) => {
        console.error("Error fetching models:", err)
      }
    }
  )

  // Enable/disable rule mutation
  const { 
    mutate: toggleRuleStatus 
  } = useApiMutation(
    async ({ rule, enabled }: { rule: AIRoutingRule, enabled: boolean }) => {
      if (enabled) {
        return aiModelsApi.enableRoutingRule(rule.id)
      } else {
        return aiModelsApi.disableRoutingRule(rule.id)
      }
    },
    {
      onSuccess: (data, { rule, enabled }) => {
        toast({
          title: `Rule ${enabled ? 'enabled' : 'disabled'}`,
          description: `${rule.name} has been ${enabled ? 'enabled' : 'disabled'}`
        })
        refetch()
      },
      onError: (error, { rule, enabled }) => {
        toast({
          title: "Error",
          description: `Failed to ${enabled ? 'enable' : 'disable'} rule: ${error.message}`,
          variant: "destructive"
        })
      }
    }
  )

  // Delete rule mutation
  const { 
    mutate: deleteRule 
  } = useApiMutation(
    async (rule: AIRoutingRule) => {
      return aiModelsApi.deleteRoutingRule(rule.id)
    },
    {
      onSuccess: (data, rule) => {
        toast({
          title: "Rule deleted",
          description: `${rule.name} has been deleted`
        })
        refetch()
      },
      onError: (error, rule) => {
        toast({
          title: "Error",
          description: `Failed to delete rule: ${error.message}`,
          variant: "destructive"
        })
      }
    }
  )

  // Handle rule status toggle
  const handleStatusToggle = (rule: AIRoutingRule, enabled: boolean) => {
    toggleRuleStatus({ rule, enabled })
  }

  // Handle rule deletion
  const handleDelete = (rule: AIRoutingRule) => {
    if (window.confirm(`Are you sure you want to delete the rule "${rule.name}"?`)) {
      deleteRule(rule)
    }
  }

  // Get model name by ID
  const getModelName = (modelId: string) => {
    if (!modelsData?.models) return modelId
    
    const model = modelsData.models.find(m => m.id === modelId)
    return model ? model.name : modelId
  }

  // Filter rules based on search query
  const rules = rulesData?.rules || []
  const filteredRules = rules
    .filter(rule => {
      if (!searchQuery) return true
      const query = searchQuery.toLowerCase()
      return (
        rule.id.toLowerCase().includes(query) ||
        rule.name.toLowerCase().includes(query) ||
        rule.targetModel.toLowerCase().includes(query) ||
        getModelName(rule.targetModel).toLowerCase().includes(query)
      )
    })

  // Define table columns
  const columns: ColumnDef<AIRoutingRule>[] = [
    {
      accessorKey: "priority",
      header: ({ column }) => (
        <div className="flex items-center">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => column.toggleSorting(column.getIsSorted() === "asc")}
          >
            Priority
            <ArrowUpDown className="ml-2 h-4 w-4" />
          </Button>
        </div>
      ),
      cell: ({ row }) => <span className="font-medium">{row.original.priority}</span>
    },
    {
      accessorKey: "name",
      header: "Name",
      cell: ({ row }) => <span className="font-medium">{row.original.name}</span>
    },
    {
      accessorKey: "condition",
      header: "Condition",
      cell: ({ row }) => {
        const condition = row.original.condition
        return (
          <div className="flex flex-col gap-1">
            <span className="text-sm font-medium">{condition.type}</span>
            {condition.type !== 'DEFAULT' && (
              <span className="text-xs text-muted-foreground">Pattern: {condition.pattern}</span>
            )}
          </div>
        )
      }
    },
    {
      accessorKey: "targetModel",
      header: "Target Model",
      cell: ({ row }) => {
        const modelId = row.original.targetModel
        const modelName = getModelName(modelId)
        
        return (
          <Badge variant="outline" className="bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-300 border-blue-200">
            {modelName}
          </Badge>
        )
      }
    },
    {
      accessorKey: "status",
      header: "Status",
      cell: ({ row }) => {
        const rule = row.original
        return (
          <div className="flex items-center space-x-2">
            <Switch
              checked={rule.enabled}
              onCheckedChange={(checked) => handleStatusToggle(rule, checked)}
              id={`rule-status-${rule.id}`}
            />
            <Badge variant={rule.enabled ? "default" : "secondary"}>
              {rule.enabled ? "Enabled" : "Disabled"}
            </Badge>
          </div>
        )
      }
    },
    {
      id: "actions",
      header: "Actions",
      cell: ({ row }) => {
        const rule = row.original
        return (
          <div className="flex justify-end space-x-1">
            <Button variant="ghost" size="icon" onClick={() => router.push(`/dashboard/ai/routing/${rule.id}/edit`)}>
              <Pencil className="h-4 w-4" />
              <span className="sr-only">Edit</span>
            </Button>
            <Button variant="ghost" size="icon" onClick={() => handleDelete(rule)}>
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
            <h1 className="text-3xl font-bold">AI Routing Rules</h1>
            <p className="text-muted-foreground">
              Manage routing rules for AI model selection
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
            <Button onClick={() => router.push('/dashboard/ai/routing/create')}>
              <PlusCircle className="mr-2 h-4 w-4" />
              Add Rule
            </Button>
          </div>
        </div>

        <Card className="mt-6">
          <CardHeader>
            <CardTitle>Routing Rules</CardTitle>
            <CardDescription>
              Configure rules for routing requests to specific AI models
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              <div className="flex items-center space-x-2">
                <div className="relative flex-1 max-w-sm">
                  <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
                  <Input
                    placeholder="Search rules..."
                    className="pl-8"
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                  />
                </div>
              </div>

              {isLoading ? (
                <div className="flex justify-center py-8">
                  <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
                </div>
              ) : filteredRules.length === 0 ? (
                <div className="text-center py-8 text-muted-foreground">
                  No routing rules found. {searchQuery && "Try adjusting your search."}
                </div>
              ) : (
                <DataTable
                  columns={columns}
                  data={filteredRules}
                  searchColumn="name"
                  searchPlaceholder="Filter by name..."
                  initialSorting={[{ id: 'priority', desc: true }]}
                />
              )}
            </div>
          </CardContent>
        </Card>
      </div>
    </DashboardLayout>
  )
}
