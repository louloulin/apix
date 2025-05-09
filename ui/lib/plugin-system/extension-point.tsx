"use client"

import { ReactNode } from 'react'
import { usePlugin } from './plugin-provider'
import { ExtensionPointType, Extension } from './types'

interface ExtensionPointProps {
  type: ExtensionPointType
  filter?: (extension: Extension) => boolean
  renderExtension?: (extension: Extension) => ReactNode
  fallback?: ReactNode
}

/**
 * Extension Point component
 * 
 * Renders extensions for a specific extension point type
 */
export function ExtensionPoint({
  type,
  filter,
  renderExtension,
  fallback
}: ExtensionPointProps) {
  const { getExtensions, isInitialized } = usePlugin()
  
  // Get extensions
  const extensions = getExtensions(type)
  
  // Filter extensions
  const filteredExtensions = filter ? extensions.filter(filter) : extensions
  
  // If no extensions and fallback provided, render fallback
  if (filteredExtensions.length === 0 && fallback) {
    return <>{fallback}</>
  }
  
  // Render extensions
  return (
    <>
      {filteredExtensions.map(extension => {
        // If custom renderer provided, use it
        if (renderExtension) {
          return <div key={extension.id}>{renderExtension(extension)}</div>
        }
        
        // Otherwise, render component if available
        const Component = (extension as any).component
        
        if (Component) {
          return <Component key={extension.id} extension={extension} />
        }
        
        // If no component, render nothing
        return null
      })}
    </>
  )
}
