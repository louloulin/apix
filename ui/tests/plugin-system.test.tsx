import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import '@testing-library/jest-dom'
import { PluginProvider, usePlugin } from '../lib/plugin-system/plugin-provider'
import { ExtensionPoint } from '../lib/plugin-system/extension-point'
import { Plugin, ExtensionPointType } from '../lib/plugin-system/types'

// Sample plugin for testing
const samplePlugin: Plugin = {
  meta: {
    id: 'test-plugin',
    name: 'Test Plugin',
    description: 'A test plugin',
    version: '1.0.0',
    author: 'Test Author'
  },
  extensions: [
    {
      type: ExtensionPointType.DASHBOARD_WIDGET,
      id: 'test-widget',
      pluginId: 'test-plugin',
      title: 'Test Widget',
      component: () => <div data-testid="test-widget">Test Widget</div>
    }
  ]
}

// Test component that uses the plugin hook
function TestComponent() {
  const { plugins, getExtensions } = usePlugin()
  const extensions = getExtensions(ExtensionPointType.DASHBOARD_WIDGET)
  
  return (
    <div>
      <div data-testid="plugin-count">{plugins.length}</div>
      <div data-testid="extension-count">{extensions.length}</div>
    </div>
  )
}

describe('Plugin System', () => {
  describe('PluginProvider', () => {
    it('provides plugin context', () => {
      render(
        <PluginProvider plugins={[samplePlugin]}>
          <TestComponent />
        </PluginProvider>
      )
      
      // The plugin count should be 1
      expect(screen.getByTestId('plugin-count')).toHaveTextContent('1')
      
      // The extension count should be 1
      expect(screen.getByTestId('extension-count')).toHaveTextContent('1')
    })
    
    it('renders children correctly', () => {
      render(
        <PluginProvider plugins={[samplePlugin]}>
          <div data-testid="test-child">Test Child</div>
        </PluginProvider>
      )
      
      // The component should render the children
      expect(screen.getByTestId('test-child')).toBeInTheDocument()
    })
  })
  
  describe('ExtensionPoint', () => {
    it('renders extensions correctly', () => {
      render(
        <PluginProvider plugins={[samplePlugin]}>
          <ExtensionPoint type={ExtensionPointType.DASHBOARD_WIDGET} />
        </PluginProvider>
      )
      
      // The component should render the extension
      expect(screen.getByTestId('test-widget')).toBeInTheDocument()
    })
    
    it('renders fallback when no extensions', () => {
      render(
        <PluginProvider plugins={[]}>
          <ExtensionPoint 
            type={ExtensionPointType.DASHBOARD_WIDGET} 
            fallback={<div data-testid="fallback">Fallback</div>}
          />
        </PluginProvider>
      )
      
      // The component should render the fallback
      expect(screen.getByTestId('fallback')).toBeInTheDocument()
    })
    
    it('filters extensions correctly', () => {
      const plugin: Plugin = {
        meta: {
          id: 'test-plugin',
          name: 'Test Plugin',
          description: 'A test plugin',
          version: '1.0.0',
          author: 'Test Author'
        },
        extensions: [
          {
            type: ExtensionPointType.DASHBOARD_WIDGET,
            id: 'test-widget-1',
            pluginId: 'test-plugin',
            title: 'Test Widget 1',
            component: () => <div data-testid="test-widget-1">Test Widget 1</div>
          },
          {
            type: ExtensionPointType.DASHBOARD_WIDGET,
            id: 'test-widget-2',
            pluginId: 'test-plugin',
            title: 'Test Widget 2',
            component: () => <div data-testid="test-widget-2">Test Widget 2</div>
          }
        ]
      }
      
      render(
        <PluginProvider plugins={[plugin]}>
          <ExtensionPoint 
            type={ExtensionPointType.DASHBOARD_WIDGET} 
            filter={(extension) => extension.id === 'test-widget-1'}
          />
        </PluginProvider>
      )
      
      // The component should render only the filtered extension
      expect(screen.getByTestId('test-widget-1')).toBeInTheDocument()
      expect(screen.queryByTestId('test-widget-2')).not.toBeInTheDocument()
    })
  })
})
