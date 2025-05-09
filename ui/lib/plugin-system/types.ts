import { ReactNode } from 'react'

/**
 * Plugin metadata
 */
export interface PluginMeta {
  id: string
  name: string
  description: string
  version: string
  author: string
  icon?: ReactNode
}

/**
 * Extension point types
 */
export enum ExtensionPointType {
  DASHBOARD_WIDGET = 'dashboard-widget',
  NAVIGATION_ITEM = 'navigation-item',
  SETTINGS_PANEL = 'settings-panel',
  ROUTE_DETAIL = 'route-detail',
  SERVICE_DETAIL = 'service-detail',
  PLUGIN_DETAIL = 'plugin-detail',
  AI_MODEL_DETAIL = 'ai-model-detail',
  CUSTOM_PAGE = 'custom-page'
}

/**
 * Base extension point interface
 */
export interface ExtensionPoint {
  type: ExtensionPointType
  id: string
  pluginId: string
  priority?: number
}

/**
 * Dashboard widget extension
 */
export interface DashboardWidgetExtension extends ExtensionPoint {
  type: ExtensionPointType.DASHBOARD_WIDGET
  title: string
  description?: string
  width?: 'full' | 'half' | 'third' | 'quarter'
  height?: 'small' | 'medium' | 'large'
  component: React.ComponentType<any>
  props?: Record<string, any>
}

/**
 * Navigation item extension
 */
export interface NavigationItemExtension extends ExtensionPoint {
  type: ExtensionPointType.NAVIGATION_ITEM
  title: string
  icon?: ReactNode
  path: string
  parent?: string
  component?: React.ComponentType<any>
}

/**
 * Settings panel extension
 */
export interface SettingsPanelExtension extends ExtensionPoint {
  type: ExtensionPointType.SETTINGS_PANEL
  title: string
  description?: string
  icon?: ReactNode
  component: React.ComponentType<any>
}

/**
 * Detail view extension
 */
export interface DetailExtension extends ExtensionPoint {
  title: string
  description?: string
  component: React.ComponentType<any>
}

/**
 * Route detail extension
 */
export interface RouteDetailExtension extends DetailExtension {
  type: ExtensionPointType.ROUTE_DETAIL
}

/**
 * Service detail extension
 */
export interface ServiceDetailExtension extends DetailExtension {
  type: ExtensionPointType.SERVICE_DETAIL
}

/**
 * Plugin detail extension
 */
export interface PluginDetailExtension extends DetailExtension {
  type: ExtensionPointType.PLUGIN_DETAIL
}

/**
 * AI model detail extension
 */
export interface AIModelDetailExtension extends DetailExtension {
  type: ExtensionPointType.AI_MODEL_DETAIL
}

/**
 * Custom page extension
 */
export interface CustomPageExtension extends ExtensionPoint {
  type: ExtensionPointType.CUSTOM_PAGE
  title: string
  description?: string
  path: string
  component: React.ComponentType<any>
}

/**
 * Union type of all extension types
 */
export type Extension =
  | DashboardWidgetExtension
  | NavigationItemExtension
  | SettingsPanelExtension
  | RouteDetailExtension
  | ServiceDetailExtension
  | PluginDetailExtension
  | AIModelDetailExtension
  | CustomPageExtension

/**
 * Plugin interface
 */
export interface Plugin {
  meta: PluginMeta
  extensions: Extension[]
  initialize?: () => Promise<void>
  cleanup?: () => Promise<void>
}
