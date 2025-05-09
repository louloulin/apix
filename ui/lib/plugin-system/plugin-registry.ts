"use client"

import { Plugin, Extension, ExtensionPointType } from './types'

/**
 * Plugin Registry
 * 
 * Manages the registration and retrieval of plugins and their extensions
 */
class PluginRegistry {
  private plugins: Map<string, Plugin> = new Map()
  private extensions: Map<ExtensionPointType, Extension[]> = new Map()
  private initialized = false
  
  /**
   * Register a plugin
   */
  registerPlugin(plugin: Plugin): void {
    // Check if plugin is already registered
    if (this.plugins.has(plugin.meta.id)) {
      console.warn(`Plugin with ID ${plugin.meta.id} is already registered. Skipping.`)
      return
    }
    
    // Register plugin
    this.plugins.set(plugin.meta.id, plugin)
    
    // Register extensions
    plugin.extensions.forEach(extension => {
      // Set plugin ID on extension
      extension.pluginId = plugin.meta.id
      
      // Get extensions for this type
      const extensions = this.extensions.get(extension.type) || []
      
      // Add extension
      extensions.push(extension)
      
      // Sort extensions by priority (higher first)
      extensions.sort((a, b) => (b.priority || 0) - (a.priority || 0))
      
      // Update extensions
      this.extensions.set(extension.type, extensions)
    })
  }
  
  /**
   * Unregister a plugin
   */
  unregisterPlugin(pluginId: string): void {
    // Check if plugin is registered
    if (!this.plugins.has(pluginId)) {
      console.warn(`Plugin with ID ${pluginId} is not registered. Skipping.`)
      return
    }
    
    // Get plugin
    const plugin = this.plugins.get(pluginId)!
    
    // Remove extensions
    plugin.extensions.forEach(extension => {
      const extensions = this.extensions.get(extension.type) || []
      const index = extensions.findIndex(e => e.id === extension.id)
      
      if (index !== -1) {
        extensions.splice(index, 1)
        this.extensions.set(extension.type, extensions)
      }
    })
    
    // Remove plugin
    this.plugins.delete(pluginId)
  }
  
  /**
   * Get all registered plugins
   */
  getPlugins(): Plugin[] {
    return Array.from(this.plugins.values())
  }
  
  /**
   * Get a plugin by ID
   */
  getPlugin(pluginId: string): Plugin | undefined {
    return this.plugins.get(pluginId)
  }
  
  /**
   * Get all extensions for a specific extension point type
   */
  getExtensions<T extends Extension>(type: ExtensionPointType): T[] {
    return (this.extensions.get(type) || []) as T[]
  }
  
  /**
   * Get an extension by ID
   */
  getExtension<T extends Extension>(type: ExtensionPointType, id: string): T | undefined {
    const extensions = this.extensions.get(type) || []
    return extensions.find(e => e.id === id) as T | undefined
  }
  
  /**
   * Initialize all plugins
   */
  async initialize(): Promise<void> {
    if (this.initialized) {
      return
    }
    
    // Initialize plugins
    for (const plugin of this.plugins.values()) {
      if (plugin.initialize) {
        try {
          await plugin.initialize()
        } catch (error) {
          console.error(`Failed to initialize plugin ${plugin.meta.id}:`, error)
        }
      }
    }
    
    this.initialized = true
  }
  
  /**
   * Clean up all plugins
   */
  async cleanup(): Promise<void> {
    if (!this.initialized) {
      return
    }
    
    // Clean up plugins
    for (const plugin of this.plugins.values()) {
      if (plugin.cleanup) {
        try {
          await plugin.cleanup()
        } catch (error) {
          console.error(`Failed to clean up plugin ${plugin.meta.id}:`, error)
        }
      }
    }
    
    this.initialized = false
  }
}

// Create singleton instance
export const pluginRegistry = new PluginRegistry()
