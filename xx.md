~~CacheProtectionManagerTest > test prevent cache avalanche(VertxTestContext) FAILED~~
    ~~kotlin.UninitializedPropertyAccessException at CacheProtectionManagerTest.kt:177~~ ✅ 已修复

~~CacheProtectionManagerTest > test prevent cache breakdown(VertxTestContext) FAILED~~
    ~~kotlin.UninitializedPropertyAccessException at CacheProtectionManagerTest.kt:129~~ ✅ 已修复

~~CacheProtectionManagerTest > test prevent cache penetration(VertxTestContext) FAILED~~
    ~~kotlin.UninitializedPropertyAccessException at CacheProtectionManagerTest.kt:80~~ ✅ 已修复

~~CacheWarmupManagerTest > test warmup cache async(VertxTestContext) FAILED~~
    ~~kotlin.UninitializedPropertyAccessException at CacheWarmupManagerTest.kt:95~~ ✅ 已修复

~~CacheWarmupManagerTest > test warmup cache sync(VertxTestContext) FAILED~~
    ~~kotlin.UninitializedPropertyAccessException at CacheWarmupManagerTest.kt:149~~ ✅ 已修复

~~CacheWarmupManagerTest > test get warmup status(VertxTestContext) FAILED~~
    ~~kotlin.UninitializedPropertyAccessException at CacheWarmupManagerTest.kt:178~~ ✅ 已修复

~~VectorIndexTest > testAddAndSearch() FAILED~~
    ~~org.opentest4j.AssertionFailedError at VectorIndexTest.kt:40~~ ✅ 已修复

~~VectorIndexTest > testSetShardData() FAILED~~
    ~~org.opentest4j.AssertionFailedError at VectorIndexTest.kt:109~~ ✅ 已修复

CDNVerticleTest > testPrewarmCache(Vertx, VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: io.vertx.core.impl.NoStackTraceThrowable

CDNVerticleTest > testGetCDNStatus(Vertx, VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: io.vertx.core.impl.NoStackTraceThrowable

CDNVerticleTest > testPurgeCache(Vertx, VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: io.vertx.core.impl.NoStackTraceThrowable

PluginChainTest > should get plugin execution stats(VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: org.opentest4j.AssertionFailedError at PluginChainTest.kt:657

ConcurrencyControllerTest > test get service metrics(VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: org.opentest4j.AssertionFailedError at ConcurrencyControllerTest.kt:128

GracefulScaleDownManagerTest > test start and complete graceful scale down(VertxTestContext) FAILED
    org.opentest4j.AssertionFailedError at GracefulScaleDownManagerTest.kt:163

GracefulScaleDownManagerTest > test get status(VertxTestContext) FAILED
    java.util.concurrent.RejectedExecutionException at GracefulScaleDownManagerTest.kt:112

ElasticScalingManagerTest > test set node count(VertxTestContext) FAILED
    java.util.concurrent.RejectedExecutionException at ElasticScalingManagerTest.kt:94

FaultInjectionManagerTest > test set fault injection mode(VertxTestContext) FAILED
    java.lang.ClassCastException at FaultInjectionManagerTest.kt:51

FaultInjectionManagerTest > test inject fault(VertxTestContext) FAILED
    java.lang.ClassCastException at FaultInjectionManagerTest.kt:51

FaultInjectionManagerTest > test should inject fault(VertxTestContext) FAILED
    java.lang.ClassCastException at FaultInjectionManagerTest.kt:51

FallbackManagerTest > test set system degradation level(VertxTestContext) FAILED
    java.lang.ClassCastException at FallbackManagerTest.kt:64

FallbackManagerTest > test apply fallback(VertxTestContext) FAILED
    java.lang.ClassCastException at FallbackManagerTest.kt:64

CircuitBreakerManagerTest > test execute with circuit breaker(VertxTestContext) FAILED
    java.lang.ClassCastException at CircuitBreakerManagerTest.kt:56

CircuitBreakerManagerTest > test get circuit breaker(VertxTestContext) FAILED
    java.lang.ClassCastException at CircuitBreakerManagerTest.kt:56

RequestValidatorPluginTest > testBodyParameterValidation(VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: java.lang.AssertionError at RequestValidatorPluginTest.kt:343

SignatureVerificationPluginTest > testSignatureVerificationWithRequestBody(VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: java.lang.AssertionError at SignatureVerificationPluginTest.kt:349

SignatureVerificationPluginTest > testSignatureVerificationFromQuery(VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: java.lang.AssertionError at SignatureVerificationPluginTest.kt:258

CsrfProtectionPluginTest > testCsrfProtectionWithSessionStorage(VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: java.lang.AssertionError at CsrfProtectionPluginTest.kt:264

CsrfProtectionPluginTest > testCsrfProtectionWithCookieStorage(VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: java.lang.AssertionError at CsrfProtectionPluginTest.kt:120

ResiliencePluginTest > testFallback(VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: java.lang.AssertionError at ResiliencePluginTest.kt:490

ResiliencePluginTest > testCircuitBreaker(VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: java.lang.AssertionError at ResiliencePluginTest.kt:292

P2PAccelerationVerticleTest > testGetBestRoute(Vertx, VertxTestContext) FAILED
    java.util.concurrent.RejectedExecutionException at SingleThreadEventExecutor.java:934

P2PAccelerationVerticleTest > testGetP2PStatus(Vertx, VertxTestContext) FAILED
    java.util.concurrent.RejectedExecutionException at SingleThreadEventExecutor.java:934

AnycastVerticleTest > testAddBGPSession(Vertx, VertxTestContext) FAILED
    java.util.concurrent.RejectedExecutionException at SingleThreadEventExecutor.java:934

AnycastVerticleTest > testAddAnycastIP(Vertx, VertxTestContext) FAILED
    java.util.concurrent.RejectedExecutionException at SingleThreadEventExecutor.java:934

LeaderElectionServiceTest > test get status(VertxTestContext) FAILED
    java.util.concurrent.RejectedExecutionException at LeaderElectionServiceTest.kt:38

LeaderElectionServiceTest > test leader election in non-clustered mode(VertxTestContext) FAILED
    java.util.concurrent.RejectedExecutionException at LeaderElectionServiceTest.kt:38

HighAvailabilityManagerTest > test get status(VertxTestContext) FAILED
    java.util.concurrent.RejectedExecutionException at HighAvailabilityManagerTest.kt:42

IncrementalSyncStrategyTest > testSyncWithIncrementalSync(VertxTestContext) FAILED
    java.lang.ClassCastException at JsonObject.java:582

IncrementalSyncStrategyTest > testSyncWithError(VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: org.opentest4j.AssertionFailedError at IncrementalSyncStrategyTest.kt:232

IncrementalSyncStrategyTest > testSyncWithLargeVersionGap(VertxTestContext) FAILED
    java.lang.ClassCastException at JsonObject.java:582

IncrementalSyncStrategyTest > testSyncWithCurrentVersionUpToDate(VertxTestContext) FAILED
    java.lang.ClassCastException at JsonObject.java:582

EventBusManagerTest > testEventBusManagerSendAndPublish(VertxTestContext) FAILED
    java.util.concurrent.TimeoutException at RecursiveAction.java:194

OptimizedEventBusTest > testResetStats(VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: org.opentest4j.AssertionFailedError at OptimizedEventBusTest.kt:304

DataDiffCalculatorTest > test apply diff with complex changes() FAILED
    org.opentest4j.AssertionFailedError at DataDiffCalculatorTest.kt:257

OptimizedEventBusTest > testSwitchEventBusType(VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: org.opentest4j.AssertionFailedError at OptimizedEventBusTest.kt:202

BandwidthAwareSyncStrategyTest > testSyncWithPoorNetworkCondition(VertxTestContext) FAILED
    org.opentest4j.AssertionFailedError at BandwidthAwareSyncStrategyTest.kt:165

ServiceMeshFeaturesTest > testEnableConsulConnectSecurity(VertxTestContext) FAILED
    java.util.concurrent.RejectedExecutionException at ServiceMeshFeaturesTest.kt:207

ServiceMeshFeaturesTest > testEnableIstioSecurity(VertxTestContext) FAILED
    java.util.concurrent.RejectedExecutionException at ServiceMeshFeaturesTest.kt:182

ServiceMeshFeaturesTest > testEnableIstioTrafficManagement(VertxTestContext) FAILED
    java.util.concurrent.RejectedExecutionException at ServiceMeshFeaturesTest.kt:136

IstioIntegrationManagerTest > testGetAllResourceStatus(VertxTestContext) FAILED
    java.lang.NullPointerException at IstioIntegrationManagerTest.kt:430

PluginChainTest > should cache plugin execution results(VertxTestContext) FAILED
    java.util.concurrent.TimeoutException at RecursiveAction.java:194

MemoryManagerTest > test get memory usage(VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: org.opentest4j.AssertionFailedError at MemoryManagerTest.kt:71

NetworkOptimizerTest > testCreateOptimizedHttpServerOptions(VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: java.lang.AssertionError at NetworkOptimizerTest.kt:46

SystemMonitorTest > testGetSystemMetrics(VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: java.lang.AssertionError at SystemMonitorTest.kt:44

DBlessVerticleTest > test save config(VertxTestContext) FAILED
    io.vertx.core.eventbus.ReplyException at DBlessVerticleTest.kt:215

K8sDeployManagerTest > testUninstallCRD(VertxTestContext) FAILED
    java.lang.NullPointerException at K8sDeployManagerTest.kt:156

K8sDeployManagerTest > testGetAllCRDInstallStatus(VertxTestContext) FAILED
    java.lang.NullPointerException at K8sDeployManagerTest.kt:194

DBlessVerticleTest > test get config(VertxTestContext) FAILED
    io.vertx.core.eventbus.ReplyException at DBlessVerticleTest.kt:123

DBlessVerticleTest > test partial update(VertxTestContext) FAILED
    io.vertx.core.eventbus.ReplyException at DBlessVerticleTest.kt:283

OptimizedEventBusTest > testHighConcurrencySend(VertxTestContext) FAILED
    java.util.concurrent.TimeoutException at RecursiveAction.java:194

DBlessVerticleTest > test get config section(VertxTestContext) FAILED
    io.vertx.core.eventbus.ReplyException at DBlessVerticleTest.kt:167

MultiCloudDeployManagerTest > testGetStatus(VertxTestContext) FAILED
    java.util.concurrent.TimeoutException at VertxExtension.java:216

OptimizedEventBusTest > testSendMessage(VertxTestContext) FAILED
    java.util.concurrent.TimeoutException at RecursiveAction.java:194

ElasticScalingVerticleTest > test set node count(VertxTestContext) FAILED
    java.util.concurrent.TimeoutException at VertxExtension.java:216

MultiCloudDeployManagerTest > testGetAllDeployStatus(VertxTestContext) FAILED
    java.util.concurrent.TimeoutException at VertxExtension.java:216

MultiCloudDeployManagerTest > testExecuteDeploy(VertxTestContext) FAILED
    java.util.concurrent.RejectedExecutionException at MultiCloudDeployManagerTest.kt:255

EdgeControlVerticleTest > testGetAuditLogs(Vertx, VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: java.lang.AssertionError at EdgeControlVerticleTest.kt:249

EdgeControlVerticleTest > testGetNodeDetails(Vertx, VertxTestContext) FAILED
    java.util.concurrent.RejectedExecutionException at SingleThreadEventExecutor.java:934

EdgeControlVerticleTest > testGetRoles(Vertx, VertxTestContext) FAILED
    java.util.concurrent.RejectedExecutionException at SingleThreadEventExecutor.java:934

EdgeControlVerticleTest > testGetNodes(Vertx, VertxTestContext) FAILED
    java.util.concurrent.RejectedExecutionException at SingleThreadEventExecutor.java:934

EdgeControlVerticleTest > testGetNodeGroups(Vertx, VertxTestContext) FAILED
    java.util.concurrent.RejectedExecutionException at SingleThreadEventExecutor.java:934

EdgeControlVerticleTest > testGetEdgeControlStatus(Vertx, VertxTestContext) FAILED
    java.util.concurrent.RejectedExecutionException at SingleThreadEventExecutor.java:934

PipelineManagerTest > testUpdatePipeline(VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: java.lang.AssertionError at PipelineManagerTest.kt:336

PipelineManagerTest > testUpdateTemplate(VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: java.lang.AssertionError at PipelineManagerTest.kt:199

PipelineManagerTest > testExecutePipeline(VertxTestContext) FAILED
    java.lang.IllegalArgumentException at PipelineManagerTest.kt:370

EdgeNodeVerticleTest > testUpdateResourceLimits(VertxTestContext) FAILED
    java.util.concurrent.RejectedExecutionException at SingleThreadEventExecutor.java:934

EdgeNodeVerticleTest > testGetResourceUsage(VertxTestContext) FAILED
    java.util.concurrent.RejectedExecutionException at SingleThreadEventExecutor.java:934

EdgeNodeVerticleTest > testGetNodeStatus(VertxTestContext) FAILED
    java.util.concurrent.RejectedExecutionException at SingleThreadEventExecutor.java:934

SmartDNSVerticleTest > testGetBestNode(Vertx, VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: io.vertx.core.impl.NoStackTraceThrowable

SmartDNSVerticleTest > testGetGeoLocation(Vertx, VertxTestContext) FAILED
    java.util.concurrent.RejectedExecutionException at SingleThreadEventExecutor.java:934

SmartDNSVerticleTest > testGetDNSStatus(Vertx, VertxTestContext) FAILED
    java.util.concurrent.RejectedExecutionException at SingleThreadEventExecutor.java:934

OptimizedEventBusTest > testPublishMessage(VertxTestContext) FAILED
    java.util.concurrent.TimeoutException at RecursiveAction.java:194

ElasticScalingVerticleTest > test get scaling status(VertxTestContext) FAILED
    java.util.concurrent.TimeoutException at VertxExtension.java:216

OptimizedEventBusTest > testJCToolsEventBusStats(VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: org.opentest4j.AssertionFailedError at OptimizedEventBusTest.kt:83

MemoryManagerVerticleTest > test get memory usage(VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: org.opentest4j.AssertionFailedError at MemoryManagerVerticleTest.kt:67

MultiLevelCacheVerticleTest > test put and get cache(VertxTestContext) FAILED
    java.util.concurrent.RejectedExecutionException at SingleThreadEventExecutor.java:934

MultiLevelCacheVerticleTest > test get cache stats(VertxTestContext) FAILED
    java.util.concurrent.RejectedExecutionException at SingleThreadEventExecutor.java:934

MultiLevelCacheVerticleTest > test start warmup(VertxTestContext) FAILED
    java.util.concurrent.RejectedExecutionException at SingleThreadEventExecutor.java:934

MultiLevelCacheVerticleTest > test remove cache(VertxTestContext) FAILED
    java.util.concurrent.RejectedExecutionException at SingleThreadEventExecutor.java:934

ResilienceVerticleTest > test set fallback level(VertxTestContext) FAILED
    java.util.concurrent.RejectedExecutionException at SingleThreadEventExecutor.java:934

ResilienceVerticleTest > test get resilience status(VertxTestContext) FAILED
    java.util.concurrent.RejectedExecutionException at SingleThreadEventExecutor.java:934

SmartCacheVerticleTest > test get bloom filter status(VertxTestContext) FAILED
    java.util.concurrent.RejectedExecutionException at SingleThreadEventExecutor.java:934

SmartCacheVerticleTest > test get hot data status(VertxTestContext) FAILED
    java.util.concurrent.RejectedExecutionException at SingleThreadEventExecutor.java:934

SmartCacheVerticleTest > test get cache protection status(VertxTestContext) FAILED
    java.util.concurrent.RejectedExecutionException at SingleThreadEventExecutor.java:934

SmartCacheVerticleTest > test get adaptive TTL status(VertxTestContext) FAILED
    java.util.concurrent.RejectedExecutionException at SingleThreadEventExecutor.java:934

HighAvailabilityVerticleTest > test control plane probe(VertxTestContext) FAILED
    java.util.concurrent.RejectedExecutionException at SingleThreadEventExecutor.java:934

HighAvailabilityVerticleTest > test get ha status(VertxTestContext) FAILED
    java.util.concurrent.TimeoutException at VertxExtension.java:216

495 tests completed, 97 failed, 30 skipped

> Task :test FAILED

FAILURE: Build failed with an exception.

* What went wrong:
Execution failed for task ':test'.
> There were failing tests. See the report at: file:///Users/louloulin/Documents/linchong/actor/apix/build/reports/tests/test/index.html

* Try:
> Run with --scan to get full insights.

Deprecated Gradle features were used in this build, making it incompatible with Gradle 9.0.

You can use '--warning-mode all' to show the individual deprecation warnings and determine if they come from your own scripts or plugins.

For more on this, please refer to https://docs.gradle.org/8.10.2/userguide/command_line_interface.html#sec:command_line_warnings in the Gradle documentation.

BUILD FAILED in 3m 7s
