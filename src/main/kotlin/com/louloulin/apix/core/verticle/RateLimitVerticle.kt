package com.louloulin.apix.core.verticle

import com.louloulin.apix.core.ratelimit.RateLimiter
import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * 限流 Verticle，负责管理限流功能
 */
class RateLimitVerticle : AbstractVerticle() {
    private val logger = LoggerFactory.getLogger(RateLimitVerticle::class.java)
    private lateinit var rateLimiter: RateLimiter
    
    override fun start(startPromise: Promise<Void>) {
        logger.info("启动 RateLimitVerticle")
        
        try {
            // 创建限流器
            rateLimiter = RateLimiter(vertx)
            
            // 初始化限流器
            val config = config().getJsonObject("ratelimit", JsonObject())
            rateLimiter.initialize(config)
            
            logger.info("RateLimitVerticle 启动成功")
            startPromise.complete()
        } catch (e: Exception) {
            logger.error("RateLimitVerticle 启动失败", e)
            startPromise.fail(e)
        }
    }
    
    override fun stop(stopPromise: Promise<Void>) {
        logger.info("停止 RateLimitVerticle")
        stopPromise.complete()
    }
}
