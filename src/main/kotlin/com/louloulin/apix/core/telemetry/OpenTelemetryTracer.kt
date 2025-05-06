package com.louloulin.apix.core.telemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.context.propagation.TextMapSetter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.samplers.Sampler
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * OpenTelemetry追踪器，用于实现分布式追踪。
 * 提供了创建、管理和导出追踪数据的功能。
 */
class OpenTelemetryTracer(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(OpenTelemetryTracer::class.java)
    
    // OpenTelemetry实例
    private lateinit var openTelemetry: OpenTelemetry
    
    // 追踪器
    private lateinit var tracer: Tracer
    
    // 服务名称
    private var serviceName = "apix-gateway"
    
    // 是否已初始化
    private val initialized = AtomicBoolean(false)
    
    // 采样率
    private var samplingRatio = 0.1
    
    // 统计信息
    private val tracesCreated = AtomicLong(0)
    private val tracesExported = AtomicLong(0)
    private val traceErrors = AtomicLong(0)
    
    // HTTP请求头提取器
    private val httpHeadersGetter = object : TextMapGetter<HttpServerRequest> {
        override fun keys(carrier: HttpServerRequest): Iterable<String> {
            return carrier.headers().names()
        }
        
        override fun get(carrier: HttpServerRequest, key: String): String? {
            return carrier.getHeader(key)
        }
    }
    
    // HTTP响应头设置器
    private val httpHeadersSetter = object : TextMapSetter<HttpServerResponse> {
        override fun set(carrier: HttpServerResponse, key: String, value: String) {
            carrier.putHeader(key, value)
        }
    }
    
    /**
     * 初始化OpenTelemetry
     */
    fun initialize(config: JsonObject = JsonObject()): OpenTelemetryTracer {
        if (initialized.compareAndSet(false, true)) {
            try {
                // 从配置中获取服务名称
                serviceName = config.getString("service_name", serviceName)
                
                // 从配置中获取采样率
                samplingRatio = config.getDouble("sampling_ratio", samplingRatio)
                
                // 从配置中获取导出器配置
                val exporterConfig = config.getJsonObject("exporter", JsonObject())
                val exporterType = exporterConfig.getString("type", "otlp")
                val exporterEndpoint = exporterConfig.getString("endpoint", "http://localhost:4317")
                
                // 创建资源
                val resource = Resource.getDefault()
                    .merge(Resource.create(Attributes.builder()
                        .put(ResourceAttributes.SERVICE_NAME, serviceName)
                        .put(ResourceAttributes.SERVICE_VERSION, "1.0.0")
                        .build()))
                
                // 创建追踪器提供者
                val tracerProviderBuilder = SdkTracerProvider.builder()
                    .setResource(resource)
                    .setSampler(Sampler.traceIdRatioBased(samplingRatio))
                
                // 添加导出器
                when (exporterType) {
                    "otlp" -> {
                        // 创建OTLP导出器
                        val spanExporter = OtlpGrpcSpanExporter.builder()
                            .setEndpoint(exporterEndpoint)
                            .setTimeout(10, TimeUnit.SECONDS)
                            .build()
                        
                        // 添加批处理导出器
                        tracerProviderBuilder.addSpanProcessor(
                            BatchSpanProcessor.builder(spanExporter)
                                .setScheduleDelay(1000, TimeUnit.MILLISECONDS)
                                .setMaxQueueSize(2048)
                                .setMaxExportBatchSize(512)
                                .build()
                        )
                    }
                    "logging" -> {
                        // 创建日志导出器
                        val spanExporter = LoggingSpanExporter()
                        
                        // 添加简单导出器
                        tracerProviderBuilder.addSpanProcessor(
                            SimpleSpanProcessor.create(spanExporter)
                        )
                    }
                    else -> {
                        logger.warn("未知的导出器类型: {}, 使用日志导出器", exporterType)
                        
                        // 创建日志导出器
                        val spanExporter = LoggingSpanExporter()
                        
                        // 添加简单导出器
                        tracerProviderBuilder.addSpanProcessor(
                            SimpleSpanProcessor.create(spanExporter)
                        )
                    }
                }
                
                // 创建追踪器提供者
                val tracerProvider = tracerProviderBuilder.build()
                
                // 创建OpenTelemetry SDK
                openTelemetry = OpenTelemetrySdk.builder()
                    .setTracerProvider(tracerProvider)
                    .setPropagators(io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator.getInstance())
                    .build()
                
                // 获取追踪器
                tracer = openTelemetry.getTracer("com.louloulin.apix")
                
                logger.info("OpenTelemetry已初始化: service={}, sampling_ratio={}, exporter={}",
                    serviceName, samplingRatio, exporterType)
            } catch (e: Exception) {
                logger.error("初始化OpenTelemetry时发生错误", e)
                initialized.set(false)
            }
        }
        
        return this
    }
    
    /**
     * 创建中间件，用于追踪HTTP请求
     */
    fun createTracingMiddleware(): (RoutingContext) -> Unit {
        return { context ->
            // 确保已初始化
            if (!initialized.get()) {
                initialize()
            }
            
            // 提取上下文
            val extractedContext = openTelemetry.propagators.textMapPropagator
                .extract(Context.current(), context.request(), httpHeadersGetter)
            
            // 创建Span
            val span = tracer.spanBuilder("HTTP ${context.request().method()} ${context.request().path()}")
                .setSpanKind(SpanKind.SERVER)
                .setParent(extractedContext)
                .startSpan()
            
            // 增加追踪创建计数
            tracesCreated.incrementAndGet()
            
            try {
                // 添加请求属性
                span.setAttribute("http.method", context.request().method().name())
                span.setAttribute("http.url", context.request().absoluteURI())
                span.setAttribute("http.host", context.request().host())
                span.setAttribute("http.path", context.request().path())
                span.setAttribute("http.user_agent", context.request().getHeader("User-Agent") ?: "Unknown")
                span.setAttribute("http.client_ip", context.request().remoteAddress().host())
                
                // 将Span存储在上下文中
                context.put("otel.span", span)
                context.put("otel.context", Context.current().with(span))
                
                // 添加响应处理器
                context.addHeadersEndHandler { v ->
                    try {
                        // 获取状态码
                        val statusCode = context.response().statusCode()
                        
                        // 添加响应属性
                        span.setAttribute("http.status_code", statusCode)
                        
                        // 设置Span状态
                        if (statusCode >= 400) {
                            span.setStatus(StatusCode.ERROR)
                            traceErrors.incrementAndGet()
                        } else {
                            span.setStatus(StatusCode.OK)
                        }
                        
                        // 注入响应头
                        openTelemetry.propagators.textMapPropagator
                            .inject(Context.current().with(span), context.response(), httpHeadersSetter)
                    } catch (e: Exception) {
                        logger.error("处理响应头时发生错误", e)
                    }
                }
                
                // 添加响应结束处理器
                context.addEndHandler { ar ->
                    try {
                        // 结束Span
                        span.end()
                        
                        // 增加追踪导出计数
                        tracesExported.incrementAndGet()
                    } catch (e: Exception) {
                        logger.error("结束Span时发生错误", e)
                    }
                }
                
                // 继续处理请求
                context.next()
            } catch (e: Exception) {
                // 设置Span状态为错误
                span.setStatus(StatusCode.ERROR, e.message ?: "Unknown error")
                span.recordException(e)
                
                // 结束Span
                span.end()
                
                // 增加追踪错误计数
                traceErrors.incrementAndGet()
                
                // 继续处理请求
                context.next()
            }
        }
    }
    
    /**
     * 创建Span
     */
    fun createSpan(name: String, parent: Context? = null): Span {
        // 确保已初始化
        if (!initialized.get()) {
            initialize()
        }
        
        // 创建Span构建器
        val spanBuilder = tracer.spanBuilder(name)
        
        // 设置父Span
        if (parent != null) {
            spanBuilder.setParent(parent)
        }
        
        // 创建Span
        val span = spanBuilder.startSpan()
        
        // 增加追踪创建计数
        tracesCreated.incrementAndGet()
        
        return span
    }
    
    /**
     * 获取当前Span
     */
    fun getCurrentSpan(): Span {
        return Span.current()
    }
    
    /**
     * 获取当前上下文
     */
    fun getCurrentContext(): Context {
        return Context.current()
    }
    
    /**
     * 获取统计信息
     */
    fun getStats(): JsonObject {
        return JsonObject()
            .put("initialized", initialized.get())
            .put("service_name", serviceName)
            .put("sampling_ratio", samplingRatio)
            .put("traces_created", tracesCreated.get())
            .put("traces_exported", tracesExported.get())
            .put("trace_errors", traceErrors.get())
    }
    
    /**
     * 设置采样率
     */
    fun setSamplingRatio(ratio: Double) {
        samplingRatio = ratio
        
        // 重新初始化
        initialized.set(false)
        initialize()
        
        logger.info("已设置采样率: {}", ratio)
    }
    
    companion object {
        // 单例实例
        @Volatile
        private var INSTANCE: OpenTelemetryTracer? = null
        
        /**
         * 获取OpenTelemetryTracer的单例实例
         */
        fun getInstance(vertx: Vertx): OpenTelemetryTracer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OpenTelemetryTracer(vertx).also { INSTANCE = it }
            }
        }
    }
}
