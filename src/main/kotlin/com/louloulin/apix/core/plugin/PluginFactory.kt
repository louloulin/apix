package com.louloulin.apix.core.plugin

/**
 * 插件工厂接口，用于创建插件实例。
 */
interface PluginFactory {
    /**
     * 创建插件实例
     * 
     * @param context 插件上下文
     * @return 创建的插件实例
     */
    fun createPlugin(context: PluginContext): Plugin
    
    /**
     * 获取插件类型
     * 
     * @return 插件类型
     */
    fun getPluginType(): PluginType
    
    /**
     * 获取插件工厂名称
     * 
     * @return 插件工厂名称
     */
    fun getName(): String {
        return this.javaClass.simpleName.removeSuffix("Factory")
    }
}
