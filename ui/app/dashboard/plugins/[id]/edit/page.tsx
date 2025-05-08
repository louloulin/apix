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
import { ArrowLeftIcon, SaveIcon } from "lucide-react"
import { getPlugin, updatePlugin, getPluginTypes } from "@/lib/api/plugins"
import { formatJson, parseJson } from "@/lib/utils/plugins"
import { Plugin, PluginType } from "@/lib/api/plugins"

export default function EditPluginPage({ params }: { params: { id: string } }) {
  const router = useRouter()
  const [plugin, setPlugin] = useState<Plugin | null>(null)
  const [pluginTypes, setPluginTypes] = useState<PluginType[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [configJson, setConfigJson] = useState("")
  const [formState, setFormState] = useState({
    type: "",
    enabled: false
  })

  // Fetch plugin details and available plugin types
  useEffect(() => {
    async function fetchData() {
      setLoading(true)
      try {
        // Fetch plugin details
        const pluginResponse = await getPlugin(params.id)
        if (pluginResponse.success) {
          setPlugin(pluginResponse.data)
          setConfigJson(formatJson(pluginResponse.data.config))
          setFormState({
            type: pluginResponse.data.type,
            enabled: pluginResponse.data.status === 'enabled'
          })
        } else {
          toast({
            title: "Error fetching plugin",
            description: pluginResponse.error,
            variant: "destructive"
          })
        }
        
        // Fetch plugin types
        const typesResponse = await getPluginTypes()
        if (typesResponse.success) {
          setPluginTypes(typesResponse.data)
        }
      } catch (error) {
        toast({
          title: "Error",
          description: "Failed to fetch plugin data",
          variant: "destructive"
        })
      } finally {
        setLoading(false)
      }
    }
    
    fetchData()
  }, [params.id])

  // Handle form submission
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    
    if (!plugin) return
    
    // Validate JSON
    let configObject
    try {
      configObject = parseJson(configJson)
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
    
    setSaving(true)
    
    try {
      const updatedPlugin = {
        type: formState.type,
        config: configObject,
        status: formState.enabled ? 'enabled' : 'disabled'
      }
      
      const response = await updatePlugin(plugin.id, updatedPlugin)
      
      if (response.success) {
        toast({
          title: "Plugin updated",
          description: `${plugin.id} has been updated successfully`
        })
        
        // Navigate back to plugin details
        router.push(`/dashboard/plugins/${plugin.id}`)
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
        description: "Failed to update plugin",
        variant: "destructive"
      })
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return (
      <DashboardLayout>
        <div className="flex justify-center items-center h-[calc(100vh-200px)]">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary"></div>
        </div>
      </DashboardLayout>
    )
  }

  if (!plugin) {
    return (
      <DashboardLayout>
        <div className="flex flex-col items-center justify-center h-[calc(100vh-200px)]">
          <h2 className="text-2xl font-bold mb-2">Plugin Not Found</h2>
          <p className="text-muted-foreground mb-4">The plugin you're looking for doesn't exist or has been deleted.</p>
          <Button onClick={() => router.push('/dashboard/plugins')}>
            <ArrowLeftIcon className="mr-2 h-4 w-4" />
            Back to Plugins
          </Button>
        </div>
      </DashboardLayout>
    )
  }

  return (
    <DashboardLayout>
      <div className="flex flex-col gap-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={() => router.push(`/dashboard/plugins/${plugin.id}`)}>
              <ArrowLeftIcon className="mr-2 h-4 w-4" />
              Back
            </Button>
            <h1 className="text-3xl font-bold">Edit Plugin: {plugin.id}</h1>
          </div>
          <Button onClick={handleSubmit} disabled={saving}>
            {saving ? (
              <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white mr-2"></div>
            ) : (
              <SaveIcon className="mr-2 h-4 w-4" />
            )}
            Save Changes
          </Button>
        </div>
        
        <Card>
          <CardHeader>
            <CardTitle>Plugin Configuration</CardTitle>
            <CardDescription>
              Edit the plugin settings and configuration
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit} className="space-y-6">
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="plugin-id">Plugin ID</Label>
                  <Input 
                    id="plugin-id" 
                    value={plugin.id} 
                    disabled 
                  />
                  <p className="text-sm text-muted-foreground">Plugin ID cannot be changed</p>
                </div>
                
                <div className="space-y-2">
                  <Label htmlFor="plugin-type">Plugin Type</Label>
                  <Select 
                    value={formState.type} 
                    onValueChange={(value) => setFormState({...formState, type: value})}
                  >
                    <SelectTrigger id="plugin-type">
                      <SelectValue placeholder="Select type" />
                    </SelectTrigger>
                    <SelectContent>
                      {pluginTypes.length > 0 ? (
                        pluginTypes.map(type => (
                          <SelectItem key={type.id} value={type.id}>
                            {type.name}
                          </SelectItem>
                        ))
                      ) : (
                        <SelectItem value={plugin.type}>{plugin.type}</SelectItem>
                      )}
                    </SelectContent>
                  </Select>
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
                      <Label htmlFor="plugin-enabled">Enabled</Label>
                    </div>
                  </div>
                  <Textarea 
                    id="plugin-config" 
                    value={configJson}
                    onChange={(e) => setConfigJson(e.target.value)}
                    className="font-mono h-[400px]"
                  />
                </div>
              </div>
            </form>
          </CardContent>
        </Card>
      </div>
    </DashboardLayout>
  )
}
