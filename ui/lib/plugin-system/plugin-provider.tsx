"use client"

import { createContext, useContext, useEffect, useState, ReactNode } from 'react'
import { pluginRegistry } from './plugin-registry'
import { Plugin, Extension, ExtensionPointType } from './types'

// Create context
interface PluginContextType {
  plugins: Plugin[]
  getExtensions: <T extends Extension>(type: ExtensionPointType) => T[]
  getExtension: <T extends Extension>(type: ExtensionPointType, id: string) => T | undefined
  isInitialized: boolean
}

const PluginContext = createContext<PluginContextType>({
  plugins: [],
  getExtensions: () => [],
  getExtension: () => undefined,
  isInitialized: false
})

// Plugin provider props
interface PluginProviderProps {
  children: ReactNode
  plugins?: Plugin[]
}

/**
 * Plugin Provider
 * 
 * Provides access to the plugin registry and manages plugin lifecycle
 */
export function PluginProvider({ children, plugins = [] }: PluginProviderProps) {
  const [isInitialized, setIsInitialized] = useState(false)
  
  // Register plugins
  useEffect(() => {
    // Register plugins
    plugins.forEach(plugin => {
      pluginRegistry.registerPlugin(plugin)
    })
    
    // Initialize plugins
    pluginRegistry.initialize().then(() => {
      setIsInitialized(true)
    })
    
    // Clean up
    return () => {
      pluginRegistry.cleanup().then(() => {
        setIsInitialized(false)
      })
    }
  }, [plugins])
  
  // Context value
  const value: PluginContextType = {
    plugins: pluginRegistry.getPlugins(),
    getExtensions: <T extends Extension>(type: ExtensionPointType) => pluginRegistry.getExtensions<T>(type),
    getExtension: <T extends Extension>(type: ExtensionPointType, id: string) => pluginRegistry.getExtension<T>(type, id),
    isInitialized
  }
  
  return (
    <PluginContext.Provider value={value}>
      {children}
    </PluginContext.Provider>
  )
}

/**
 * Use Plugin hook
 * 
 * Provides access to the plugin context
 */
export function usePlugin() {
  return useContext(PluginContext)
}
