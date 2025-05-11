package com.louloulin.apix.resource

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.file.FileSystem
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.Deflater
import java.util.zip.GZIPOutputStream

/**
 * 资源优化器
 * 负责静态资源的自动压缩、图像优化、代码压缩和懒加载支持
 * 实现plan7.md中的3.3.3节"资源优化"功能
 */
class ResourceOptimizer(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(ResourceOptimizer::class.java)
    
    // 资源优化配置
    private val optimizationConfig = AtomicReference<JsonObject>(JsonObject())
    
    // 资源优化是否启用
    private val optimizationEnabled = AtomicBoolean(false)
    
    // 资源管理器
    private lateinit var resourceManager: ResourceManager
    
    // 优化缓存
    private val optimizationCache = ConcurrentHashMap<String, OptimizationInfo>()
    
    // 文件系统
    private val fs: FileSystem = vertx.fileSystem()
    
    /**
     * 获取ResourceOptimizer实例
     */
    companion object {
        private var instance: ResourceOptimizer? = null
        
        @Synchronized
        fun getInstance(vertx: Vertx): ResourceOptimizer {
            if (instance == null) {
                instance = ResourceOptimizer(vertx)
            }
            return instance!!
        }
    }
    
    /**
     * 初始化资源优化器
     * 
     * @param config 资源优化配置
     * @return Future<Void> 初始化结果
     */
    fun initialize(config: JsonObject): Future<Void> {
        logger.info("初始化资源优化器")
        
        val promise = Promise.promise<Void>()
        
        try {
            // 保存配置
            this.optimizationConfig.set(config)
            
            // 获取资源优化启用状态
            val enabled = config.getBoolean("enabled", false)
            this.optimizationEnabled.set(enabled)
            
            if (!enabled) {
                logger.info("资源优化功能已禁用")
                promise.complete()
                return promise.future()
            }
            
            // 获取资源管理器
            resourceManager = ResourceManager.getInstance(vertx)
            
            // 清空优化缓存
            optimizationCache.clear()
            
            logger.info("资源优化器初始化完成")
            promise.complete()
        } catch (e: Exception) {
            logger.error("资源优化器初始化失败", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 优化资源
     * 
     * @param path 资源路径
     * @param content 资源内容
     * @return Future<Buffer> 优化后的内容
     */
    fun optimizeResource(path: String, content: Buffer): Future<Buffer> {
        val promise = Promise.promise<Buffer>()
        
        if (!optimizationEnabled.get()) {
            return Future.succeededFuture(content)
        }
        
        // 获取优化配置
        val config = optimizationConfig.get()
        
        // 检查是否已经优化过
        val cachedOptimization = optimizationCache[path]
        if (cachedOptimization != null && cachedOptimization.originalHash == content.hashCode()) {
            return Future.succeededFuture(cachedOptimization.optimizedContent)
        }
        
        // 根据文件类型选择优化方法
        val extension = File(path).extension.lowercase()
        
        when (extension) {
            "js" -> {
                // JavaScript优化
                optimizeJavaScript(path, content, config)
                    .onSuccess { optimized ->
                        // 缓存优化结果
                        optimizationCache[path] = OptimizationInfo(
                            originalHash = content.hashCode(),
                            optimizedContent = optimized
                        )
                        
                        promise.complete(optimized)
                    }
                    .onFailure { cause ->
                        logger.error("JavaScript优化失败: {}", path, cause)
                        promise.complete(content) // 失败时返回原始内容
                    }
            }
            "css" -> {
                // CSS优化
                optimizeCSS(path, content, config)
                    .onSuccess { optimized ->
                        // 缓存优化结果
                        optimizationCache[path] = OptimizationInfo(
                            originalHash = content.hashCode(),
                            optimizedContent = optimized
                        )
                        
                        promise.complete(optimized)
                    }
                    .onFailure { cause ->
                        logger.error("CSS优化失败: {}", path, cause)
                        promise.complete(content) // 失败时返回原始内容
                    }
            }
            "html", "htm" -> {
                // HTML优化
                optimizeHTML(path, content, config)
                    .onSuccess { optimized ->
                        // 缓存优化结果
                        optimizationCache[path] = OptimizationInfo(
                            originalHash = content.hashCode(),
                            optimizedContent = optimized
                        )
                        
                        promise.complete(optimized)
                    }
                    .onFailure { cause ->
                        logger.error("HTML优化失败: {}", path, cause)
                        promise.complete(content) // 失败时返回原始内容
                    }
            }
            "jpg", "jpeg", "png", "gif", "webp", "svg" -> {
                // 图像优化
                optimizeImage(path, content, config)
                    .onSuccess { optimized ->
                        // 缓存优化结果
                        optimizationCache[path] = OptimizationInfo(
                            originalHash = content.hashCode(),
                            optimizedContent = optimized
                        )
                        
                        promise.complete(optimized)
                    }
                    .onFailure { cause ->
                        logger.error("图像优化失败: {}", path, cause)
                        promise.complete(content) // 失败时返回原始内容
                    }
            }
            else -> {
                // 其他类型，不优化
                promise.complete(content)
            }
        }
        
        return promise.future()
    }
    
    /**
     * 优化JavaScript
     * 
     * @param path 资源路径
     * @param content 资源内容
     * @param config 优化配置
     * @return Future<Buffer> 优化后的内容
     */
    private fun optimizeJavaScript(path: String, content: Buffer, config: JsonObject): Future<Buffer> {
        val promise = Promise.promise<Buffer>()
        
        // 获取JavaScript优化配置
        val jsConfig = config.getJsonObject("js", JsonObject())
        val minify = jsConfig.getBoolean("minify", true)
        
        if (!minify) {
            return Future.succeededFuture(content)
        }
        
        vertx.executeBlocking<Buffer>({ blockingPromise ->
            try {
                // 在实际实现中，这里应该使用JavaScript压缩库
                // 例如：UglifyJS、Terser等
                // 这里只是一个简单的示例，移除注释和多余的空白
                
                val contentStr = content.toString()
                
                // 移除单行注释
                var result = contentStr.replace(Regex("//.*"), "")
                
                // 移除多行注释
                result = result.replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
                
                // 移除多余的空白
                result = result.replace(Regex("\\s+"), " ")
                
                // 移除行首和行尾的空白
                result = result.replace(Regex("^\\s+|\\s+$", RegexOption.MULTILINE), "")
                
                blockingPromise.complete(Buffer.buffer(result))
            } catch (e: Exception) {
                blockingPromise.fail(e)
            }
        }, { ar ->
            if (ar.succeeded()) {
                promise.complete(ar.result())
            } else {
                promise.fail(ar.cause())
            }
        })
        
        return promise.future()
    }
    
    /**
     * 优化CSS
     * 
     * @param path 资源路径
     * @param content 资源内容
     * @param config 优化配置
     * @return Future<Buffer> 优化后的内容
     */
    private fun optimizeCSS(path: String, content: Buffer, config: JsonObject): Future<Buffer> {
        val promise = Promise.promise<Buffer>()
        
        // 获取CSS优化配置
        val cssConfig = config.getJsonObject("css", JsonObject())
        val minify = cssConfig.getBoolean("minify", true)
        
        if (!minify) {
            return Future.succeededFuture(content)
        }
        
        vertx.executeBlocking<Buffer>({ blockingPromise ->
            try {
                // 在实际实现中，这里应该使用CSS压缩库
                // 例如：cssnano、clean-css等
                // 这里只是一个简单的示例，移除注释和多余的空白
                
                val contentStr = content.toString()
                
                // 移除注释
                var result = contentStr.replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
                
                // 移除多余的空白
                result = result.replace(Regex("\\s+"), " ")
                
                // 移除行首和行尾的空白
                result = result.replace(Regex("^\\s+|\\s+$", RegexOption.MULTILINE), "")
                
                // 移除分号后的空白
                result = result.replace(Regex(";\\s+"), ";")
                
                // 移除冒号后的空白
                result = result.replace(Regex(":\\s+"), ":")
                
                // 移除逗号后的空白
                result = result.replace(Regex(",\\s+"), ",")
                
                // 移除左大括号前的空白
                result = result.replace(Regex("\\s+\\{"), "{")
                
                // 移除右大括号前的空白
                result = result.replace(Regex("\\s+\\}"), "}")
                
                blockingPromise.complete(Buffer.buffer(result))
            } catch (e: Exception) {
                blockingPromise.fail(e)
            }
        }, { ar ->
            if (ar.succeeded()) {
                promise.complete(ar.result())
            } else {
                promise.fail(ar.cause())
            }
        })
        
        return promise.future()
    }
    
    /**
     * 优化HTML
     * 
     * @param path 资源路径
     * @param content 资源内容
     * @param config 优化配置
     * @return Future<Buffer> 优化后的内容
     */
    private fun optimizeHTML(path: String, content: Buffer, config: JsonObject): Future<Buffer> {
        val promise = Promise.promise<Buffer>()
        
        // 获取HTML优化配置
        val htmlConfig = config.getJsonObject("html", JsonObject())
        val minify = htmlConfig.getBoolean("minify", true)
        val lazyLoad = htmlConfig.getBoolean("lazyLoad", true)
        
        if (!minify && !lazyLoad) {
            return Future.succeededFuture(content)
        }
        
        vertx.executeBlocking<Buffer>({ blockingPromise ->
            try {
                var contentStr = content.toString()
                
                if (minify) {
                    // 在实际实现中，这里应该使用HTML压缩库
                    // 例如：html-minifier等
                    // 这里只是一个简单的示例，移除注释和多余的空白
                    
                    // 移除HTML注释
                    contentStr = contentStr.replace(Regex("<!--[\\s\\S]*?-->"), "")
                    
                    // 移除多余的空白
                    contentStr = contentStr.replace(Regex(">\\s+<"), "><")
                    
                    // 移除行首和行尾的空白
                    contentStr = contentStr.replace(Regex("^\\s+|\\s+$", RegexOption.MULTILINE), "")
                }
                
                if (lazyLoad) {
                    // 添加懒加载支持
                    // 为图片添加loading="lazy"属性
                    contentStr = contentStr.replace(
                        Regex("<img([^>]*)>"),
                        { matchResult ->
                            val attributes = matchResult.groupValues[1]
                            if (attributes.contains("loading=")) {
                                // 已经有loading属性，不修改
                                matchResult.value
                            } else {
                                "<img${attributes} loading=\"lazy\">"
                            }
                        }
                    )
                    
                    // 为iframe添加loading="lazy"属性
                    contentStr = contentStr.replace(
                        Regex("<iframe([^>]*)>"),
                        { matchResult ->
                            val attributes = matchResult.groupValues[1]
                            if (attributes.contains("loading=")) {
                                // 已经有loading属性，不修改
                                matchResult.value
                            } else {
                                "<iframe${attributes} loading=\"lazy\">"
                            }
                        }
                    )
                }
                
                blockingPromise.complete(Buffer.buffer(contentStr))
            } catch (e: Exception) {
                blockingPromise.fail(e)
            }
        }, { ar ->
            if (ar.succeeded()) {
                promise.complete(ar.result())
            } else {
                promise.fail(ar.cause())
            }
        })
        
        return promise.future()
    }
    
    /**
     * 优化图像
     * 
     * @param path 资源路径
     * @param content 资源内容
     * @param config 优化配置
     * @return Future<Buffer> 优化后的内容
     */
    private fun optimizeImage(path: String, content: Buffer, config: JsonObject): Future<Buffer> {
        val promise = Promise.promise<Buffer>()
        
        // 获取图像优化配置
        val imageConfig = config.getJsonObject("image", JsonObject())
        val optimize = imageConfig.getBoolean("optimize", true)
        
        if (!optimize) {
            return Future.succeededFuture(content)
        }
        
        // 获取图像类型
        val extension = File(path).extension.lowercase()
        
        vertx.executeBlocking<Buffer>({ blockingPromise ->
            try {
                // 在实际实现中，这里应该使用图像优化库
                // 例如：ImageMagick、Sharp等
                // 这里只是一个示例，返回原始内容
                
                blockingPromise.complete(content)
            } catch (e: Exception) {
                blockingPromise.fail(e)
            }
        }, { ar ->
            if (ar.succeeded()) {
                promise.complete(ar.result())
            } else {
                promise.fail(ar.cause())
            }
        })
        
        return promise.future()
    }
    
    /**
     * 压缩资源
     * 
     * @param content 资源内容
     * @param compressionLevel 压缩级别
     * @return Future<Buffer> 压缩后的内容
     */
    fun compressResource(content: Buffer, compressionLevel: Int = Deflater.BEST_COMPRESSION): Future<Buffer> {
        val promise = Promise.promise<Buffer>()
        
        if (!optimizationEnabled.get()) {
            return Future.succeededFuture(content)
        }
        
        vertx.executeBlocking<Buffer>({ blockingPromise ->
            try {
                val baos = ByteArrayOutputStream()
                val gzos = GZIPOutputStream(baos)
                gzos.write(content.bytes)
                gzos.close()
                
                val compressed = Buffer.buffer(baos.toByteArray())
                blockingPromise.complete(compressed)
            } catch (e: Exception) {
                blockingPromise.fail(e)
            }
        }, { ar ->
            if (ar.succeeded()) {
                promise.complete(ar.result())
            } else {
                promise.fail(ar.cause())
            }
        })
        
        return promise.future()
    }
    
    /**
     * 生成懒加载脚本
     * 
     * @param resources 资源列表
     * @return Future<String> 懒加载脚本
     */
    fun generateLazyLoadingScript(resources: List<String>): Future<String> {
        val promise = Promise.promise<String>()
        
        if (!optimizationEnabled.get()) {
            return Future.succeededFuture("")
        }
        
        // 获取懒加载配置
        val lazyLoadConfig = optimizationConfig.get().getJsonObject("lazyLoad", JsonObject())
        val enabled = lazyLoadConfig.getBoolean("enabled", true)
        
        if (!enabled || resources.isEmpty()) {
            return Future.succeededFuture("")
        }
        
        vertx.executeBlocking<String>({ blockingPromise ->
            try {
                val script = StringBuilder()
                script.append("document.addEventListener('DOMContentLoaded', function() {\n")
                
                // 添加懒加载函数
                script.append("  function lazyLoad(url, type) {\n")
                script.append("    var element;\n")
                script.append("    if (type === 'script') {\n")
                script.append("      element = document.createElement('script');\n")
                script.append("      element.src = url;\n")
                script.append("    } else if (type === 'style') {\n")
                script.append("      element = document.createElement('link');\n")
                script.append("      element.rel = 'stylesheet';\n")
                script.append("      element.href = url;\n")
                script.append("    }\n")
                script.append("    if (element) {\n")
                script.append("      document.head.appendChild(element);\n")
                script.append("    }\n")
                script.append("  }\n\n")
                
                // 添加IntersectionObserver
                script.append("  if ('IntersectionObserver' in window) {\n")
                script.append("    var lazyObserver = new IntersectionObserver(function(entries, observer) {\n")
                script.append("      entries.forEach(function(entry) {\n")
                script.append("        if (entry.isIntersecting) {\n")
                script.append("          var target = entry.target;\n")
                script.append("          var url = target.dataset.src;\n")
                script.append("          var type = target.dataset.type;\n")
                script.append("          if (url) {\n")
                script.append("            lazyLoad(url, type);\n")
                script.append("            target.removeAttribute('data-src');\n")
                script.append("            target.removeAttribute('data-type');\n")
                script.append("            lazyObserver.unobserve(target);\n")
                script.append("          }\n")
                script.append("        }\n")
                script.append("      });\n")
                script.append("    });\n\n")
                
                // 添加资源
                for (resource in resources) {
                    val extension = File(resource).extension.lowercase()
                    val type = when (extension) {
                        "js" -> "script"
                        "css" -> "style"
                        else -> continue
                    }
                    
                    script.append("    var placeholder = document.createElement('div');\n")
                    script.append("    placeholder.dataset.src = '${resource}';\n")
                    script.append("    placeholder.dataset.type = '${type}';\n")
                    script.append("    placeholder.style.display = 'none';\n")
                    script.append("    document.body.appendChild(placeholder);\n")
                    script.append("    lazyObserver.observe(placeholder);\n")
                }
                
                script.append("  } else {\n")
                
                // 如果不支持IntersectionObserver，直接加载
                for (resource in resources) {
                    val extension = File(resource).extension.lowercase()
                    val type = when (extension) {
                        "js" -> "script"
                        "css" -> "style"
                        else -> continue
                    }
                    
                    script.append("    lazyLoad('${resource}', '${type}');\n")
                }
                
                script.append("  }\n")
                script.append("});\n")
                
                blockingPromise.complete(script.toString())
            } catch (e: Exception) {
                blockingPromise.fail(e)
            }
        }, { ar ->
            if (ar.succeeded()) {
                promise.complete(ar.result())
            } else {
                promise.fail(ar.cause())
            }
        })
        
        return promise.future()
    }
    
    /**
     * 获取资源优化状态
     * 
     * @return JsonObject 资源优化状态
     */
    fun getStatus(): JsonObject {
        val status = JsonObject()
            .put("enabled", optimizationEnabled.get())
            .put("optimizedCount", optimizationCache.size)
        
        // 获取优化配置
        val config = optimizationConfig.get()
        
        // 添加各类型优化状态
        val jsConfig = config.getJsonObject("js", JsonObject())
        status.put("js", JsonObject()
            .put("minify", jsConfig.getBoolean("minify", true))
        )
        
        val cssConfig = config.getJsonObject("css", JsonObject())
        status.put("css", JsonObject()
            .put("minify", cssConfig.getBoolean("minify", true))
        )
        
        val htmlConfig = config.getJsonObject("html", JsonObject())
        status.put("html", JsonObject()
            .put("minify", htmlConfig.getBoolean("minify", true))
            .put("lazyLoad", htmlConfig.getBoolean("lazyLoad", true))
        )
        
        val imageConfig = config.getJsonObject("image", JsonObject())
        status.put("image", JsonObject()
            .put("optimize", imageConfig.getBoolean("optimize", true))
        )
        
        return status
    }
    
    /**
     * 更新资源优化配置
     * 
     * @param config 新的资源优化配置
     * @return Future<Void> 更新结果
     */
    fun updateConfig(config: JsonObject): Future<Void> {
        logger.info("更新资源优化配置")
        
        // 清空优化缓存
        optimizationCache.clear()
        
        // 重新初始化
        return initialize(config)
    }
    
    /**
     * 关闭资源优化器
     * 
     * @return Future<Void> 关闭结果
     */
    fun close(): Future<Void> {
        logger.info("关闭资源优化器")
        
        // 清空优化缓存
        optimizationCache.clear()
        
        return Future.succeededFuture()
    }
}

/**
 * 优化信息
 */
data class OptimizationInfo(
    val originalHash: Int,
    val optimizedContent: Buffer
)
