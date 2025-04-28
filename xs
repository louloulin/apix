> Task :test

AdminVerticleTest > testGetSystemInfo(VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: java.lang.AssertionError at AdminVerticleTest.kt:318

AdminVerticleTest > testDeleteRoute(VertxTestContext) FAILED
    java.lang.AssertionError at AdminVerticleTest.kt:263

AdminVerticleTest > testUpdateRoute(VertxTestContext) FAILED
    java.lang.AssertionError at AdminVerticleTest.kt:207

AdminVerticleTest > testGetConfig(VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: java.lang.AssertionError at AdminVerticleTest.kt:296

AdminVerticleTest > testCreateAndGetRoute(VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: java.lang.AssertionError at AdminVerticleTest.kt:118

RoutingRuleTest > test rule matching() FAILED
    org.opentest4j.AssertionFailedError at RoutingRuleTest.kt:82

ClusterManagerFactoryTest > test create cluster manager with disabled clustering() FAILED
    org.opentest4j.AssertionFailedError at ClusterManagerFactoryTest.kt:24

ClusterManagerFactoryTest > test create cluster manager with unknown type() FAILED
    org.opentest4j.AssertionFailedError at ClusterManagerFactoryTest.kt:60

OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

> Task :test

RouteManagerTest > should get all routes() FAILED
    java.lang.NullPointerException at RouteManagerTest.kt:55

RouteManagerTest > should update route() FAILED
    java.lang.NullPointerException at RouteManagerTest.kt:55

RouteManagerTest > should remove route() FAILED
    java.lang.NullPointerException at RouteManagerTest.kt:55

RouteManagerTest > should set up routes on router() FAILED
    java.lang.NullPointerException at RouteManagerTest.kt:55

RouteManagerTest > should load routes from configuration() FAILED
    java.lang.NullPointerException at RouteManagerTest.kt:55

ModelRouterVerticleTest > test get rules(VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: org.opentest4j.AssertionFailedError at ModelRouterVerticleTest.kt:51

ModelRouterVerticleTest > test add rule(VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: org.opentest4j.AssertionFailedError at ModelRouterVerticleTest.kt:86

ModelRouterVerticleTest > test clear rules(VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: org.opentest4j.AssertionFailedError at ModelRouterVerticleTest.kt:179

RequestAggregationPluginTest > testTemplateAggregation(VertxTestContext) FAILED
    java.lang.ClassCastException at RequestAggregationPluginTest.kt:379

ResponseCachePluginTest > should add cache headers to response(VertxTestContext) FAILED
    java.util.concurrent.TimeoutException at VertxExtension.java:216

JwtAuthPluginTest > testJwtAuthFromQuery(VertxTestContext) FAILED
    java.lang.NullPointerException at JwtAuthPluginTest.kt:53

JwtAuthPluginTest > testJwtAuthFromHeader(VertxTestContext) FAILED
    java.lang.NullPointerException at JwtAuthPluginTest.kt:53

JwtAuthPluginTest > testJwtAuthRequiredClaims(VertxTestContext) FAILED
    java.lang.NullPointerException at JwtAuthPluginTest.kt:53

JwtAuthPluginTest > testJwtAuthRequiredScopes(VertxTestContext) FAILED
    java.lang.NullPointerException at JwtAuthPluginTest.kt:53

RequestCachePluginTest > testCacheStats(VertxTestContext) FAILED
    java.lang.AssertionError at RequestCachePluginTest.kt:487

RequestCachePluginTest > testBasicCaching(VertxTestContext) FAILED
    java.lang.AssertionError at RequestCachePluginTest.kt:169

RequestCachePluginTest > testCacheKeyGeneration(VertxTestContext) FAILED
    java.lang.AssertionError at RequestCachePluginTest.kt:312

RequestCachePluginTest > testCacheControl(VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: java.lang.AssertionError at RequestCachePluginTest.kt:373

ResiliencePluginTest > testCircuitBreaker(VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: java.lang.AssertionError at ResiliencePluginTest.kt:292

ResiliencePluginTest > testFallback(VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: java.lang.AssertionError at ResiliencePluginTest.kt:490

CsrfProtectionPluginTest > testCsrfProtectionWithCookieStorage(VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: java.lang.AssertionError at CsrfProtectionPluginTest.kt:120

CsrfProtectionPluginTest > testCsrfProtectionWithSessionStorage(VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: java.lang.AssertionError at CsrfProtectionPluginTest.kt:264

SignatureVerificationPluginTest > testSignatureVerificationFromQuery(VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: java.lang.AssertionError at SignatureVerificationPluginTest.kt:258

SignatureVerificationPluginTest > testSignatureVerificationWithRequestBody(VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: java.lang.AssertionError at SignatureVerificationPluginTest.kt:349

RequestValidatorPluginTest > testBodyParameterValidation(VertxTestContext) FAILED
    java.lang.AssertionError at VertxExtension.java:205
        Caused by: java.lang.AssertionError at RequestValidatorPluginTest.kt:343

