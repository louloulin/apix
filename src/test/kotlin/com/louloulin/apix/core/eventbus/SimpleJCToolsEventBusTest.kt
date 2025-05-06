package com.louloulin.apix.core.eventbus

import io.vertx.core.Vertx
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * 简单的JCToolsEventBus测试类
 */
@ExtendWith(VertxExtension::class)
class SimpleJCToolsEventBusTest {
    private lateinit var vertx: Vertx
    private lateinit var jcToolsEventBus: JCToolsEventBus
    private lateinit var eventBusManager: EventBusManager
    
    @BeforeEach
    fun setUp() {
        vertx = Vertx.vertx()
        jcToolsEventBus = JCToolsEventBus.getInstance(vertx)
        eventBusManager = EventBusManager.getInstance(vertx)
        
        // 启动JCToolsEventBus
        jcToolsEventBus.start()
    }
    
    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete(testContext.succeedingThenComplete())
    }
    
    /**
     * 测试获取原生EventBus
     */
    @Test
    fun testGetOriginalEventBus(testContext: VertxTestContext) {
        val originalEventBus = jcToolsEventBus.getOriginalEventBus()
        
        testContext.verify {
            assertNotNull(originalEventBus)
            testContext.completeNow()
        }
    }
    
    /**
     * 测试EventBusManager获取EventBus
     */
    @Test
    fun testGetEventBus(testContext: VertxTestContext) {
        val eventBus = eventBusManager.getEventBus()
        
        testContext.verify {
            assertNotNull(eventBus)
            testContext.completeNow()
        }
    }
    
    /**
     * 测试EventBusManager切换类型
     */
    @Test
    fun testSwitchType(testContext: VertxTestContext) {
        // 获取当前类型
        val initialType = eventBusManager.getCurrentType()
        
        // 切换到JCToolsEventBus
        eventBusManager.switchType(EventBusManager.EventBusType.JCTOOLS)
            .compose { success ->
                testContext.verify {
                    assertEquals(true, success)
                    assertEquals(EventBusManager.EventBusType.JCTOOLS, eventBusManager.getCurrentType())
                }
                
                // 切换回原生EventBus
                eventBusManager.switchType(EventBusManager.EventBusType.VERTX)
            }
            .onComplete { ar ->
                testContext.verify {
                    assertEquals(true, ar.result())
                    assertEquals(EventBusManager.EventBusType.VERTX, eventBusManager.getCurrentType())
                    testContext.completeNow()
                }
            }
    }
    
    /**
     * 测试获取统计信息
     */
    @Test
    fun testGetStats(testContext: VertxTestContext) {
        val stats = eventBusManager.getStats()
        
        testContext.verify {
            assertNotNull(stats)
            assertEquals(EventBusManager.EventBusType.VERTX.name, stats.getString("type"))
            testContext.completeNow()
        }
    }
}
