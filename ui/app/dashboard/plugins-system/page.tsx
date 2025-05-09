"use client"

import { useState } from "react"
import { DashboardLayout } from "@/components/layout/dashboard-layout"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Switch } from "@/components/ui/switch"
import { PluginProvider } from "@/lib/plugin-system/plugin-provider"
import { ExtensionPoint } from "@/lib/plugin-system/extension-point"
import { ExtensionPointType } from "@/lib/plugin-system/types"
import { samplePlugin } from "@/plugins/sample-plugin"
import { 
  Activity, 
  Package, 
  Settings, 
  RefreshCw, 
  PlusCircle,
  Trash2,
  Download,
  Upload,
  Info
} from "lucide-react"

export default function PluginsSystemPage() {
  const [activeTab, setActiveTab] = useState("installed")
  
  return (
    <PluginProvider plugins={[samplePlugin]}>
      <DashboardLayout>
        <div className="flex flex-col gap-4">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-3xl font-bold">Plugin System</h1>
              <p className="text-muted-foreground">
                Manage and configure plugins for your gateway
              </p>
            </div>
            <div className="flex gap-2">
              <Button variant="outline" size="icon">
                <RefreshCw className="h-4 w-4" />
              </Button>
              <Button>
                <PlusCircle className="mr-2 h-4 w-4" />
                Add Plugin
              </Button>
            </div>
          </div>

          <Tabs defaultValue="installed" value={activeTab} onValueChange={setActiveTab}>
            <TabsList className="grid w-full grid-cols-3">
              <TabsTrigger value="installed" className="flex items-center gap-2">
                <Package className="h-4 w-4" />
                Installed
              </TabsTrigger>
              <TabsTrigger value="marketplace" className="flex items-center gap-2">
                <Download className="h-4 w-4" />
                Marketplace
              </TabsTrigger>
              <TabsTrigger value="settings" className="flex items-center gap-2">
                <Settings className="h-4 w-4" />
                Settings
              </TabsTrigger>
            </TabsList>
            
            <TabsContent value="installed" className="space-y-4">
              <Card>
                <CardHeader>
                  <CardTitle>Installed Plugins</CardTitle>
                  <CardDescription>
                    Manage your installed plugins
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  <div className="space-y-4">
                    {/* Sample Plugin */}
                    <div className="flex items-start justify-between border-b pb-4">
                      <div className="flex-1">
                        <div className="flex items-center gap-2">
                          <Activity className="h-5 w-5 text-primary" />
                          <h3 className="text-lg font-medium">Sample Plugin</h3>
                          <Badge variant="outline" className="ml-2">v1.0.0</Badge>
                        </div>
                        <p className="text-sm text-muted-foreground mt-1">
                          A sample plugin that demonstrates the plugin system
                        </p>
                        <div className="flex items-center gap-4 mt-2">
                          <span className="text-xs text-muted-foreground">Author: APIX Team</span>
                          <Button variant="ghost" size="sm" className="h-6 px-2">
                            <Info className="h-3 w-3 mr-1" />
                            Details
                          </Button>
                          <Button variant="ghost" size="sm" className="h-6 px-2">
                            <Settings className="h-3 w-3 mr-1" />
                            Configure
                          </Button>
                        </div>
                      </div>
                      <div className="flex items-center gap-2">
                        <Switch id="sample-plugin-enabled" defaultChecked />
                        <Button variant="ghost" size="icon">
                          <Trash2 className="h-4 w-4 text-destructive" />
                        </Button>
                      </div>
                    </div>
                    
                    {/* No other plugins message */}
                    <div className="text-center py-4">
                      <p className="text-muted-foreground">No other plugins installed</p>
                      <Button variant="outline" className="mt-2">
                        <Upload className="mr-2 h-4 w-4" />
                        Upload Plugin
                      </Button>
                    </div>
                  </div>
                </CardContent>
              </Card>
              
              {/* Extension point for dashboard widgets */}
              <div className="grid gap-4 md:grid-cols-2">
                <ExtensionPoint
                  type={ExtensionPointType.DASHBOARD_WIDGET}
                  fallback={
                    <Card>
                      <CardHeader>
                        <CardTitle>No Plugin Widgets</CardTitle>
                        <CardDescription>No dashboard widgets from plugins</CardDescription>
                      </CardHeader>
                      <CardContent>
                        <p className="text-center py-4">
                          Install plugins with dashboard widgets to see them here.
                        </p>
                      </CardContent>
                    </Card>
                  }
                />
              </div>
            </TabsContent>
            
            <TabsContent value="marketplace" className="space-y-4">
              <Card>
                <CardHeader>
                  <CardTitle>Plugin Marketplace</CardTitle>
                  <CardDescription>
                    Discover and install plugins for your gateway
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  <div className="text-center py-8">
                    <p className="text-muted-foreground">Plugin marketplace is coming soon</p>
                  </div>
                </CardContent>
              </Card>
            </TabsContent>
            
            <TabsContent value="settings" className="space-y-4">
              <Card>
                <CardHeader>
                  <CardTitle>Plugin Settings</CardTitle>
                  <CardDescription>
                    Configure plugin system settings
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  <div className="space-y-4">
                    <div className="flex items-center justify-between">
                      <div>
                        <h3 className="font-medium">Enable Plugin System</h3>
                        <p className="text-sm text-muted-foreground">
                          Enable or disable the plugin system
                        </p>
                      </div>
                      <Switch id="enable-plugin-system" defaultChecked />
                    </div>
                    
                    <div className="flex items-center justify-between">
                      <div>
                        <h3 className="font-medium">Auto-update Plugins</h3>
                        <p className="text-sm text-muted-foreground">
                          Automatically update plugins when new versions are available
                        </p>
                      </div>
                      <Switch id="auto-update-plugins" />
                    </div>
                    
                    <div className="flex items-center justify-between">
                      <div>
                        <h3 className="font-medium">Plugin Sandboxing</h3>
                        <p className="text-sm text-muted-foreground">
                          Run plugins in a sandboxed environment for security
                        </p>
                      </div>
                      <Switch id="plugin-sandboxing" defaultChecked />
                    </div>
                    
                    <div className="flex items-center justify-between">
                      <div>
                        <h3 className="font-medium">Plugin Development Mode</h3>
                        <p className="text-sm text-muted-foreground">
                          Enable development features for plugin creators
                        </p>
                      </div>
                      <Switch id="plugin-dev-mode" />
                    </div>
                  </div>
                </CardContent>
              </Card>
              
              {/* Extension point for settings panels */}
              <ExtensionPoint
                type={ExtensionPointType.SETTINGS_PANEL}
                fallback={null}
              />
            </TabsContent>
          </Tabs>
        </div>
      </DashboardLayout>
    </PluginProvider>
  )
}
