package com.louloulin.apix.core.verticle

import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import org.slf4j.LoggerFactory

/**
 * 测试Verticle，用于测试资源管理器。
 */
class TestVerticle : AbstractVerticle() {
    private val logger = LoggerFactory.getLogger(TestVerticle::class.java)
    
    override fun start(startPromise: Promise<Void>) {
        logger.info("Starting TestVerticle")
        startPromise.complete()
    }
    
    override fun stop(stopPromise: Promise<Void>) {
        logger.info("Stopping TestVerticle")
        stopPromise.complete()
    }
}
