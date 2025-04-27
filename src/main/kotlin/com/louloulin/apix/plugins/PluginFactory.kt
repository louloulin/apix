package com.louloulin.apix.plugins

/**
 * Factory interface for creating plugin instances.
 */
interface PluginFactory {
    /**
     * Creates a new plugin instance with the given configuration.
     * 
     * @param config The plugin configuration
     * @return A new plugin instance
     */
    fun create(config: PluginConfig): Plugin
}
