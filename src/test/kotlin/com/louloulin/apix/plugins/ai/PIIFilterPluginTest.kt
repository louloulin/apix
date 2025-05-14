package com.louloulin.apix.plugins.ai

import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import io.vertx.core.Future
import io.vertx.core.http.HttpServerResponse
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse

@ExtendWith(VertxExtension::class)
class PIIFilterPluginTest {

    private lateinit var vertx: Vertx
    private lateinit var plugin: PIIFilterPlugin

    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx

        // Create plugin configuration
        val config = JsonObject()
            .put("enabled", true)
            .put("filter_requests", true)
            .put("filter_responses", true)
            .put("mask_mode", "partial")
            .put("log_detections", true)
            .put("custom_patterns", JsonObject()
                .put("custom_id", "ID-\\d{6}")
            )

        // Create plugin
        plugin = PIIFilterPlugin("test-pii-filter", PluginConfig("test-pii-filter", "pii-filter", config))

        // Initialize plugin
        plugin.initialize(vertx).onComplete { ar ->
            if (ar.succeeded()) {
                testContext.completeNow()
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }

    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        plugin.shutdown()
        testContext.completeNow()
    }

    @Test
    fun testFilterEmail(vertx: Vertx, testContext: VertxTestContext) {
        // Create a mock RoutingContext
        val routingContext = Mockito.mock(RoutingContext::class.java)

        // Create a request with PII data
        val requestWithPII = JsonObject()
            .put("user", JsonObject()
                .put("email", "john.doe@example.com")
                .put("name", "John Doe")
            )

        // Mock the request body
        val bodyAsString = requestWithPII.encode()
        `when`(routingContext.getBodyAsString()).thenReturn(bodyAsString)

        // Mock setBody method to capture the filtered body
        `when`(routingContext.setBody(Mockito.any())).thenAnswer { invocation ->
            val newBuffer = invocation.getArgument<Buffer>(0)
            val filteredBody = JsonObject(newBuffer.toString())

            // Verify that the email was masked
            val maskedEmail = filteredBody.getJsonObject("user").getString("email")
            assertFalse(maskedEmail.contains("john.doe"), "Email username should be masked")
            assertTrue(maskedEmail.contains("@example.com"), "Email domain should not be masked")

            // Complete the test
            testContext.completeNow()

            routingContext
        }

        // Execute the plugin
        plugin.execute(routingContext).onComplete { ar ->
            if (!testContext.completed()) {
                if (ar.succeeded()) {
                    testContext.completeNow()
                } else {
                    testContext.failNow(ar.cause())
                }
            }
        }

        // Wait for the test to complete
        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }

    @Test
    fun testFilterPhoneNumber(vertx: Vertx, testContext: VertxTestContext) {
        // Create a mock RoutingContext
        val routingContext = Mockito.mock(RoutingContext::class.java)

        // Create a request with PII data
        val requestWithPII = JsonObject()
            .put("contact", JsonObject()
                .put("phone", "+1 (555) 123-4567")
                .put("address", "123 Main St, Anytown, USA")
            )

        // Mock the request body
        val bodyAsString = requestWithPII.encode()
        `when`(routingContext.getBodyAsString()).thenReturn(bodyAsString)

        // Mock setBody method to capture the filtered body
        `when`(routingContext.setBody(Mockito.any())).thenAnswer { invocation ->
            val newBuffer = invocation.getArgument<Buffer>(0)
            val filteredBody = JsonObject(newBuffer.toString())

            // Verify that the phone number was masked
            val maskedPhone = filteredBody.getJsonObject("contact").getString("phone")
            assertTrue(maskedPhone.endsWith("4567"), "Last 4 digits should be preserved")
            assertTrue(maskedPhone.contains("****"), "First part should be masked")

            // Complete the test
            testContext.completeNow()

            routingContext
        }

        // Execute the plugin
        plugin.execute(routingContext).onComplete { ar ->
            if (!testContext.completed()) {
                if (ar.succeeded()) {
                    testContext.completeNow()
                } else {
                    testContext.failNow(ar.cause())
                }
            }
        }

        // Wait for the test to complete
        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }

    @Test
    fun testFilterCreditCard(vertx: Vertx, testContext: VertxTestContext) {
        // Create a mock RoutingContext
        val routingContext = Mockito.mock(RoutingContext::class.java)

        // Create a request with PII data
        val requestWithPII = JsonObject()
            .put("payment", JsonObject()
                .put("card_number", "4111 1111 1111 1111")
                .put("expiry", "12/25")
            )

        // Mock the request body
        val bodyAsString = requestWithPII.encode()
        `when`(routingContext.getBodyAsString()).thenReturn(bodyAsString)

        // Mock setBody method to capture the filtered body
        `when`(routingContext.setBody(Mockito.any())).thenAnswer { invocation ->
            val newBuffer = invocation.getArgument<Buffer>(0)
            val filteredBody = JsonObject(newBuffer.toString())

            // Verify that the credit card number was masked
            val maskedCard = filteredBody.getJsonObject("payment").getString("card_number")
            assertTrue(maskedCard.endsWith("1111"), "Last 4 digits should be preserved")
            assertTrue(maskedCard.contains("****"), "First part should be masked")

            // Complete the test
            testContext.completeNow()

            routingContext
        }

        // Execute the plugin
        plugin.execute(routingContext).onComplete { ar ->
            if (!testContext.completed()) {
                if (ar.succeeded()) {
                    testContext.completeNow()
                } else {
                    testContext.failNow(ar.cause())
                }
            }
        }

        // Wait for the test to complete
        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }

    @Test
    fun testFilterNestedJson(vertx: Vertx, testContext: VertxTestContext) {
        // Create a mock RoutingContext
        val routingContext = Mockito.mock(RoutingContext::class.java)

        // Create a request with nested PII data
        val requestWithPII = JsonObject()
            .put("data", JsonObject()
                .put("users", JsonArray()
                    .add(JsonObject()
                        .put("email", "alice@example.com")
                        .put("ssn", "123-45-6789")
                    )
                    .add(JsonObject()
                        .put("email", "bob@example.com")
                        .put("ssn", "987-65-4321")
                    )
                )
            )

        // Mock the request body
        val bodyAsString = requestWithPII.encode()
        `when`(routingContext.getBodyAsString()).thenReturn(bodyAsString)

        // Mock setBody method to capture the filtered body
        `when`(routingContext.setBody(Mockito.any())).thenAnswer { invocation ->
            val newBuffer = invocation.getArgument<Buffer>(0)
            val filteredBody = JsonObject(newBuffer.toString())

            // Verify that the nested PII was masked
            val users = filteredBody.getJsonObject("data").getJsonArray("users")
            val user1 = users.getJsonObject(0)
            val user2 = users.getJsonObject(1)

            // Check emails
            assertTrue(user1.getString("email").contains("@example.com"), "Email domain should not be masked")
            assertTrue(user2.getString("email").contains("@example.com"), "Email domain should not be masked")

            // Check SSNs
            assertTrue(user1.getString("ssn").startsWith("***-**-"), "SSN prefix should be masked")
            assertTrue(user1.getString("ssn").endsWith("6789"), "SSN last 4 digits should be preserved")
            assertTrue(user2.getString("ssn").startsWith("***-**-"), "SSN prefix should be masked")
            assertTrue(user2.getString("ssn").endsWith("4321"), "SSN last 4 digits should be preserved")

            // Complete the test
            testContext.completeNow()

            routingContext
        }

        // Execute the plugin
        plugin.execute(routingContext).onComplete { ar ->
            if (!testContext.completed()) {
                if (ar.succeeded()) {
                    testContext.completeNow()
                } else {
                    testContext.failNow(ar.cause())
                }
            }
        }

        // Wait for the test to complete
        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }

    @Test
    fun testCustomPattern(vertx: Vertx, testContext: VertxTestContext) {
        // Create a mock RoutingContext
        val routingContext = Mockito.mock(RoutingContext::class.java)

        // Create a request with custom PII data
        val requestWithPII = JsonObject()
            .put("user", JsonObject()
                .put("custom_id", "ID-123456")
                .put("name", "John Doe")
            )

        // Mock the request body
        val bodyAsString = requestWithPII.encode()
        `when`(routingContext.getBodyAsString()).thenReturn(bodyAsString)

        // Mock setBody method to capture the filtered body
        `when`(routingContext.setBody(Mockito.any())).thenAnswer { invocation ->
            val newBuffer = invocation.getArgument<Buffer>(0)
            val filteredBody = JsonObject(newBuffer.toString())

            // Verify that the custom ID was masked
            val maskedId = filteredBody.getJsonObject("user").getString("custom_id")
            assertFalse(maskedId.equals("ID-123456"), "Custom ID should be masked")

            // Complete the test
            testContext.completeNow()

            routingContext
        }

        // Execute the plugin
        plugin.execute(routingContext).onComplete { ar ->
            if (!testContext.completed()) {
                if (ar.succeeded()) {
                    testContext.completeNow()
                } else {
                    testContext.failNow(ar.cause())
                }
            }
        }

        // Wait for the test to complete
        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }

    @Test
    fun testDifferentMaskModes(vertx: Vertx, testContext: VertxTestContext) {
        // Test different mask modes
        testMaskMode("full", "john.doe@example.com", "[REDACTED]", vertx, testContext)
    }

    private fun testMaskMode(
        maskMode: String,
        originalValue: String,
        expectedPattern: String,
        vertx: Vertx,
        testContext: VertxTestContext
    ) {
        // Create plugin configuration with specific mask mode
        val config = JsonObject()
            .put("enabled", true)
            .put("filter_requests", true)
            .put("filter_responses", false)
            .put("mask_mode", maskMode)
            .put("log_detections", false)

        // Create plugin
        val modePlugin = PIIFilterPlugin("test-mode", PluginConfig("test-mode", "pii-filter", config))

        // Initialize plugin
        modePlugin.initialize(vertx).compose { _ ->
            // Create a mock RoutingContext
            val routingContext = Mockito.mock(RoutingContext::class.java)

            // Create a request with PII data
            val requestWithPII = JsonObject()
                .put("email", originalValue)

            // Mock the request body
            val bodyAsString = requestWithPII.encode()
            `when`(routingContext.getBodyAsString()).thenReturn(bodyAsString)

            // Mock setBody method to capture the filtered body
            `when`(routingContext.setBody(Mockito.any())).thenAnswer { invocation ->
                val newBuffer = invocation.getArgument<Buffer>(0)
                val filteredBody = JsonObject(newBuffer.toString())

                // Verify that the value was masked according to the mode
                val maskedValue = filteredBody.getString("email")

                when (maskMode) {
                    "full" -> assertTrue(maskedValue == "[REDACTED]", "Should be fully redacted")
                    "type" -> assertTrue(maskedValue == "[email]", "Should show type")
                    "remove" -> assertTrue(maskedValue == "", "Should be empty")
                    else -> assertTrue(maskedValue.contains(expectedPattern), "Should match expected pattern")
                }

                // Complete the test
                testContext.completeNow()

                routingContext
            }

            // Execute the plugin
            modePlugin.execute(routingContext).onComplete { ar ->
                if (!testContext.completed()) {
                    if (ar.succeeded()) {
                        testContext.completeNow()
                    } else {
                        testContext.failNow(ar.cause())
                    }
                }
            }

            Future.succeededFuture<Void>()
        }.onFailure { err ->
            testContext.failNow(err)
        }

        // Wait for the test to complete
        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }
}
