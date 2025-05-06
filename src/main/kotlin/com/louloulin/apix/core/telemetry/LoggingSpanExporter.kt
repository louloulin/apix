package com.louloulin.apix.core.telemetry

import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import org.slf4j.LoggerFactory

/**
 * 日志导出器，用于将追踪数据导出到日志。
 * 这个导出器主要用于开发和测试环境，不建议在生产环境中使用。
 */
class LoggingSpanExporter : SpanExporter {
    private val logger = LoggerFactory.getLogger(LoggingSpanExporter::class.java)
    
    override fun export(spans: Collection<SpanData>): CompletableResultCode {
        try {
            for (span in spans) {
                logger.info("Span: {}, TraceId={}, SpanId={}, ParentSpanId={}, Kind={}, Status={}",
                    span.name,
                    span.traceId,
                    span.spanId,
                    span.parentSpanId,
                    span.kind,
                    span.status
                )
                
                // 记录属性
                if (span.attributes.isNotEmpty) {
                    logger.info("  Attributes: {}", span.attributes)
                }
                
                // 记录事件
                if (span.events.isNotEmpty()) {
                    logger.info("  Events: {}", span.events)
                }
                
                // 记录链接
                if (span.links.isNotEmpty()) {
                    logger.info("  Links: {}", span.links)
                }
            }
            
            return CompletableResultCode.ofSuccess()
        } catch (e: Exception) {
            logger.error("导出Span时发生错误", e)
            return CompletableResultCode.ofFailure()
        }
    }
    
    override fun flush(): CompletableResultCode {
        return CompletableResultCode.ofSuccess()
    }
    
    override fun shutdown(): CompletableResultCode {
        return CompletableResultCode.ofSuccess()
    }
}
