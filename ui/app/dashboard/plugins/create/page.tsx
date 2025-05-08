"use client"

import { useState, useEffect } from "react"
import { useRouter } from "next/navigation"
import { DashboardLayout } from "@/components/layout/dashboard-layout"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import { Textarea } from "@/components/ui/textarea"
import { toast } from "@/components/ui/use-toast"
import { ArrowLeftIcon, PlusIcon } from "lucide-react"
import { createPlugin, getPluginTypes } from "@/lib/api/plugins"
import { formatJson, parseJson } from "@/lib/utils/plugins"
import { PluginType } from "@/lib/api/plugins"

export default function CreatePluginPage() {
  const router = useRouter()
  const [pluginTypes, setPluginTypes] = useState<PluginType[]>([])
  const [loading, setLoading] = useState(true)
  const [creating, setCreating] = useState(false)
  const [formState, setFormState] = useState({
    id: "",
    type: "",
    enabled: true,
    config: "{}"
  })

  // Fetch available plugin types
  useEffect(() => {
    async function fetchPluginTypes() {
      setLoading(true)
      try {
        const response = await getPluginTypes()
        if (response.success) {
          setPluginTypes(response.data)
          
          // Set default type if available
          if (response.data.length > 0) {
            setFormState(prev => ({
              ...prev,
              type: response.data[0].id
            }))
            
            // Set default config if available
            if (response.data[0].defaultConfig) {
              setFormState(prev => ({
                ...prev,
                config: formatJson(response.data[0].defaultConfig)
              }))
            }
          }
        } else {
          toast({
            title: "Error fetching plugin types",
            description: response.error,
            variant: "destructive"
          })
        }
      } catch (error) {
        toast({
          title: "Error",
          description: "Failed to fetch plugin types",
          variant: "destructive"
        })
      } finally {
        setLoading(false)
      }
    }
    
    fetchPluginTypes()
  }, [])

  // Handle type change
  const handleTypeChange = (type: string) => {
    setFormState({...formState, type})
    
    // Update config with default config for selected type
    const selectedType = pluginTypes.find(t => t.id === type)
    if (selectedType?.defaultConfig) {
      setFormState(prev => ({
        ...prev,
        config: formatJson(selectedType.defaultConfig)
      }))
    }
  }

  // Handle form submission
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    
    // Validate form
    if (!formState.id.trim()) {
      toast({
        title: "Missing plugin ID",
        description: "Please enter a plugin ID",
        variant: "destructive"
      })
      return
    }
    
    if (!formState.type) {
      toast({
        title: "Missing plugin type",
        description: "Please select a plugin type",
        variant: "destructive"
      })
      return
    }
    
    // Validate JSON
    let configObject
    try {
      configObject = parseJson(formState.config)
      if (!configObject) {
        throw new Error("Invalid JSON")
      }
    } catch (error) {
      toast({
        title: "Invalid JSON",
        description: "Please check your configuration JSON",
        variant: "destructive"
      })
      return
    }
    
    setCreating(true)
    
    try {
      const plugin = {
        id: formState.id,
        type: formState.type,
        config: configObject
      }
      
      const response = await createPlugin(plugin)
      
      if (response.success) {
        toast({
          title: "Plugin created",
          description: `${formState.id} has been created successfully`
        })
        
        // Navigate to the new plugin
        router.push(`/dashboard/plugins/${formState.id}`)
      } else {
        toast({
          title: "Error",
          description: response.error,
          variant: "destructive"
        })
      }
    } catch (error) {
      toast({
        title: "Error",
        description: "Failed to create plugin",
        variant: "destructive"
      })
    } finally {
      setCreating(false)
    }
  }

  return (
    <DashboardLayout>
      <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={() => router.push('/dashboard/plugins')}>
              <ArrowLeftIcon className="mr-2 h-4 w-4" />
              Back
            </Button>
            <h1 className="text-3xl font-bold">Create New Plugin</h1>
          </div>
          <Button onClick={handleSubmit} disabled={creating}>
            {creating ? (
              <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white mr-2"></div>
            ) : (
              <PlusIcon className="mr-2 h-4 w-4" />
            )}
            Create Plugin
          </Button>
        </div>
        
        <Card>
          <CardHeader>
            <CardTitle>Plugin Configuration</CardTitle>
            <CardDescription>
              Configure the new plugin settings
            </CardDescription>
          </CardHeader>
          <CardContent>
            {loading ? (
              <div className="flex justify-center py-8">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
              </div>
            ) : (
              <form onSubmit={handleSubmit} className="space-y-6">
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="plugin-id">Plugin ID</Label>
                    <Input 
                      id="plugin-id" 
                      placeholder="my-plugin"
                      value={formState.id}
                      onChange={(e) => setFormState({...formState, id: e.target.value})}
                      required
                    />
                    <p className="text-sm text-muted-foreground">Unique identifier for the plugin</p>
                  </div>
                  
                  <div className="space-y-2">
                    <Label htmlFor="plugin-type">Plugin Type</Label>
                    <Select 
                      value={formState.type} 
                      onValueChange={handleTypeChange}
                      required
                    >
                      <SelectTrigger id="plugin-type">
                        <SelectValue placeholder="Select type" />
                      </SelectTrigger>
                      <SelectContent>
                        {pluginTypes.map(type => (
                          <SelectItem key={type.id} value={type.id}>
                            {type.name}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <p className="text-sm text-muted-foreground">Type determines plugin behavior</p>
                  </div>
                  
                  <div className="col-span-2 space-y-2">
                    <div className="flex items-center justify-between">
                      <Label htmlFor="plugin-config">Configuration (JSON)</Label>
                      <div className="flex items-center space-x-2">
                        <Switch 
                          id="plugin-enabled"
                          checked={formState.enabled}
                          onCheckedChange={(checked) => setFormState({...formState, enabled: checked})}
                        />
                        <Label htmlFor="plugin-enabled">Enable after creation</Label>
                      </div>
                    </div>
                    <Textarea 
                      id="plugin-config" 
                      value={formState.config}
                      onChange={(e) => setFormState({...formState, config: e.target.value})}
                      className="font-mono h-[400px]"
                      required
                    />
                    <p className="text-sm text-muted-foreground">
                      JSON configuration for the plugin. The schema depends on the plugin type.
                    </p>
                  </div>
                </div>
              </form>
            )}
          </CardContent>
        </Card>
      </div>
    </DashboardLayout>
  )
}
