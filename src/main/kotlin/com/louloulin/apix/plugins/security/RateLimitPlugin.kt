package com.louloulin.apix.plugins.security

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Plugin that implements rate limiting for requests.
 */
class RateLimitPlugin(
    override val id: String,
    override val config: PluginConfig
) : Plugin {
    private val logger = LoggerFactory.getLogger(RateLimitPlugin::class.java)
    
    override val type: String = "rate-limiter"
    
    // Configuration values
    private val limit: Int = config.getInteger("limit", 100) ?: 100
    private val windowSeconds: Int = config.getInteger("window", 60) ?: 60
    private val keyExtractor: (RoutingContext) -> String = { ctx ->
        // By default, use the client's IP address as the key
        ctx.request().remoteAddress().hostAddress() ?: "unknown"
    }
    
    // Rate limiting state
    private val counters = ConcurrentHashMap<String, Counter>()
    
    private data class Counter(
        val count: AtomicInteger = AtomicInteger(0),
        val windowStart: Long = Instant.now().epochSecond
    )
    
    override fun execute(context: RoutingContext): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            val key = keyExtractor(context)
            
            // Get or create counter for this key
            val counter = counters.compute(key) { _, existing ->
                val now = Instant.now().epochSecond
                
                if (existing == null || now - existing.windowStart >= windowSeconds) {
                    // Create a new counter if none exists or the window has expired
                    Counter(AtomicInteger(1), now)
                } else {
                    // Increment the existing counter
                    existing.count.incrementAndGet()
                    existing
                }
            }
            
            // Add rate limit headers to the response
            context.response()
                .putHeader("X-RateLimit-Limit", limit.toString())
                .putHeader("X-RateLimit-Remaining", (limit - counter!!.count.get()).toString())
                .putHeader("X-RateLimit-Reset", (counter.windowStart + windowSeconds).toString())
            
            if (counter.count.get() > limit) {
                // Rate limit exceeded
                logger.debug("Rate limit exceeded for key: {}", key)
                context.response()
                    .setStatusCode(429)
                    .putHeader("Content-Type", "application/json")
                    .putHeader("Retry-After", windowSeconds.toString())
                    .end("""{"error": "Rate limit exceeded"}""")
                promise.complete() // Complete the promise to stop the chain
            } else {
                // Rate limit not exceeded
                promise.complete()
            }
        } catch (e: Exception) {
            logger.error("Error executing rate limit plugin", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    override fun shutdown() {
        // Clear counters
        counters.clear()
    }
}
