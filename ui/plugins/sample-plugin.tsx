"use client"

import { Plugin, ExtensionPointType, DashboardWidgetExtension, NavigationItemExtension } from '@/lib/plugin-system/types'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Activity, BarChart3 } from 'lucide-react'

// Sample dashboard widget component
function SampleDashboardWidget() {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Sample Plugin Widget</CardTitle>
        <CardDescription>This widget is provided by the sample plugin</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="flex flex-col items-center justify-center p-4">
          <BarChart3 className="h-16 w-16 text-primary mb-4" />
          <p className="text-center">This is a sample dashboard widget from a plugin.</p>
          <p className="text-center text-sm text-muted-foreground mt-2">
            You can extend the dashboard with custom widgets using the plugin system.
          </p>
        </div>
      </CardContent>
    </Card>
  )
}

// Sample custom page component
function SampleCustomPage() {
  return (
    <div className="space-y-4">
      <h1 className="text-3xl font-bold">Sample Plugin Page</h1>
      <p className="text-muted-foreground">
        This page is provided by the sample plugin.
      </p>
      
      <Card>
        <CardHeader>
          <CardTitle>Plugin Custom Page</CardTitle>
          <CardDescription>This page is provided by the sample plugin</CardDescription>
        </CardHeader>
        <CardContent>
          <p>
            Plugins can add custom pages to the application. This is a sample custom page
            that demonstrates this capability.
          </p>
          <p className="mt-4">
            Custom pages can contain any content and functionality that a normal page can have.
            They can also use the application's UI components and access the application's state.
          </p>
        </CardContent>
      </Card>
    </div>
  )
}

// Define the sample plugin
export const samplePlugin: Plugin = {
  meta: {
    id: 'sample-plugin',
    name: 'Sample Plugin',
    description: 'A sample plugin that demonstrates the plugin system',
    version: '1.0.0',
    author: 'APIX Team',
    icon: <Activity className="h-4 w-4" />
  },
  extensions: [
    // Dashboard widget extension
    {
      type: ExtensionPointType.DASHBOARD_WIDGET,
      id: 'sample-dashboard-widget',
      pluginId: 'sample-plugin',
      title: 'Sample Widget',
      description: 'A sample dashboard widget',
      width: 'half',
      height: 'medium',
      component: SampleDashboardWidget,
      priority: 10
    },
    // Navigation item extension
    {
      type: ExtensionPointType.NAVIGATION_ITEM,
      id: 'sample-navigation-item',
      pluginId: 'sample-plugin',
      title: 'Sample Plugin',
      icon: <Activity className="h-4 w-4" />,
      path: '/dashboard/plugins/sample',
      priority: 5
    },
    // Custom page extension
    {
      type: ExtensionPointType.CUSTOM_PAGE,
      id: 'sample-custom-page',
      pluginId: 'sample-plugin',
      title: 'Sample Plugin Page',
      description: 'A sample custom page',
      path: '/dashboard/plugins/sample',
      component: SampleCustomPage
    }
  ],
  async initialize() {
    console.log('Sample plugin initialized')
  },
  async cleanup() {
    console.log('Sample plugin cleaned up')
  }
}
