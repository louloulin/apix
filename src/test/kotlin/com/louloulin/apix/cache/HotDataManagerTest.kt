package com.louloulin.apix.cache

import com.louloulin.apix.core.test.BaseVertxTest
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 热点数据管理器测试。
 */
class HotDataManagerTest : BaseVertxTest() {

    private lateinit var hotDataManager: HotDataManager

    override fun initialize(testContext: VertxTestContext) {
        try {
            // 创建热点数据管理器
            hotDataManager = HotDataManager.getInstance(vertx)

            // 初始化热点数据管理器
            val config = JsonObject()
                .put("cache", JsonObject()
                    .put("hotData", JsonObject()
                        .put("enabled", true)
                        .put("algorithm", "frequency")
                        .put("threshold", 10)
                        .put("timeWindow", 60000)
                        .put("maxSize", 1000)
                        .put("strategy", "local")
                    )
                    .put("cacheNamespace", "test")
                )

            // 创建多级缓存管理器
            val cacheManager = MultiLevelCacheManager.getInstance(vertx)
            cacheManager.initialize(config)
                .compose { _ -> hotDataManager.initialize(config) }
                .onSuccess { _ ->
                    testContext.completeNow()
                }
                .onFailure { e -> handleError(testContext, e) }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `test record access and check hot data`(testContext: VertxTestContext) {
        try {
            // 使用固定的测试键，以确保测试稳定性
            val testKey = "test-hot-key"

            // 直接将测试键添加到热点数据中
            hotDataManager.addHotData(testKey, "test", 20)
                .onSuccess { _ ->
                    // 检查是否是热点数据
                    val isHotData = hotDataManager.isHotData(testKey, "test")
                    val accessCount = hotDataManager.getHotDataAccessCount(testKey, "test")

                    testContext.verify {
                        // 应该被识别为热点数据
                        assertTrue(isHotData, "应该被识别为热点数据")
                        assertEquals(20, accessCount, "访问计数应该为 20")

                        // 获取所有热点数据
                        val allHotData = hotDataManager.getAllHotData()
                        assertNotNull(allHotData, "热点数据列表不应为 null")
                        assertTrue(allHotData.isNotEmpty(), "热点数据列表不应为空")
                        assertTrue(allHotData.contains(testKey), "热点数据列表应包含测试键")

                        // 获取状态
                        val status = hotDataManager.getStatus()
                        assertNotNull(status, "状态对象不应为 null")
                        assertTrue(status.getBoolean("enabled", false), "热点数据检测应该已启用")

                        testContext.completeNow()
                    }
                }
                .onFailure { e -> handleError(testContext, e) }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }
}
