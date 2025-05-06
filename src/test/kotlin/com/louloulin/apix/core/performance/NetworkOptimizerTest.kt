package com.louloulin.apix.core.performance

import com.louloulin.apix.core.network.NetworkOptimizer
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.net.NetServerOptions
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * 网络优化器测试
 */
@ExtendWith(VertxExtension::class)
class NetworkOptimizerTest {
    private lateinit var vertx: Vertx
    private lateinit var networkOptimizer: NetworkOptimizer
    
    @BeforeEach
    fun setUp() {
        vertx = Vertx.vertx()
        networkOptimizer = NetworkOptimizer.getInstance(vertx)
    }
    
    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete { testContext.completeNow() }
    }
    
    @Test
    fun testCreateOptimizedHttpServerOptions(testContext: VertxTestContext) {
        // 创建优化的HTTP服务器选项
        val options = networkOptimizer.createOptimizedHttpServerOptions()
        
        testContext.verify {
            // 验证选项是否正确设置
            assert(options.isTcpNoDelay) { "应该启用TCP_NODELAY" }
            assert(options.isTcpFastOpen) { "应该启用TCP_FASTOPEN" }
            assert(options.isTcpQuickAck) { "应该启用TCP_QUICKACK" }
            assert(options.isReusePort) { "应该启用端口重用" }
            assert(options.isReuseAddress) { "应该启用地址重用" }
            assert(options.acceptBacklog == 10000) { "应该设置acceptBacklog为10000" }
            
            testContext.completeNow()
        }
    }
    
    @Test
    fun testCreateOptimizedHttpClientOptions(testContext: VertxTestContext) {
        // 创建优化的HTTP客户端选项
        val options = networkOptimizer.createOptimizedHttpClientOptions()
        
        testContext.verify {
            // 验证选项是否正确设置
            assert(options.maxPoolSize == 500) { "应该设置maxPoolSize为500" }
            assert(options.isKeepAlive) { "应该启用Keep-Alive" }
            assert(options.keepAliveTimeout == 60) { "应该设置keepAliveTimeout为60" }
            assert(options.maxWaitQueueSize == 1000) { "应该设置maxWaitQueueSize为1000" }
            assert(options.isTcpNoDelay) { "应该启用TCP_NODELAY" }
            assert(options.isTcpFastOpen) { "应该启用TCP_FASTOPEN" }
            assert(options.isTcpQuickAck) { "应该启用TCP_QUICKACK" }
            assert(options.isUseAlpn) { "应该启用ALPN" }
            
            testContext.completeNow()
        }
    }
    
    @Test
    fun testCreateOptimizedNetServerOptions(testContext: VertxTestContext) {
        // 创建优化的TCP服务器选项
        val options = networkOptimizer.createOptimizedNetServerOptions()
        
        testContext.verify {
            // 验证选项是否正确设置
            assert(options.isTcpNoDelay) { "应该启用TCP_NODELAY" }
            assert(options.isTcpFastOpen) { "应该启用TCP_FASTOPEN" }
            assert(options.isTcpQuickAck) { "应该启用TCP_QUICKACK" }
            assert(options.isReusePort) { "应该启用端口重用" }
            assert(options.isReuseAddress) { "应该启用地址重用" }
            assert(options.acceptBacklog == 10000) { "应该设置acceptBacklog为10000" }
            
            testContext.completeNow()
        }
    }
    
    @Test
    fun testGetStats(testContext: VertxTestContext) {
        // 获取网络统计信息
        val stats = networkOptimizer.getStats()
        
        testContext.verify {
            // 验证统计信息是否为JsonObject
            assert(stats is io.vertx.core.json.JsonObject) { "应该返回JsonObject" }
            
            // 验证统计信息是否包含预期的字段
            assert(stats.containsKey("activeConnections")) { "应该包含activeConnections字段" }
            assert(stats.containsKey("totalConnections")) { "应该包含totalConnections字段" }
            assert(stats.containsKey("totalBytesTransferred")) { "应该包含totalBytesTransferred字段" }
            assert(stats.containsKey("connectionPools")) { "应该包含connectionPools字段" }
            
            testContext.completeNow()
        }
    }
    
    @Test
    fun testGetOrCreateConnectionPool(testContext: VertxTestContext) {
        // 获取连接池
        val pool = networkOptimizer.getOrCreateConnectionPool("localhost", 8080, 10)
        
        testContext.verify {
            // 验证连接池是否创建
            assert(pool != null) { "应该创建连接池" }
            
            // 再次获取相同的连接池
            val samePool = networkOptimizer.getOrCreateConnectionPool("localhost", 8080, 10)
            
            // 验证是否返回相同的连接池实例
            assert(pool === samePool) { "应该返回相同的连接池实例" }
            
            testContext.completeNow()
        }
    }
}
