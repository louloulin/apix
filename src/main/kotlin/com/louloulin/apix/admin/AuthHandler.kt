package com.louloulin.apix.admin

import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.auth.JWTOptions
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * Handler for authentication API endpoints.
 */
class AuthHandler(private val jwtAuth: JWTAuth) {
    private val logger = LoggerFactory.getLogger(AuthHandler::class.java)

    // In-memory user store (replace with database in production)
    private val users = mutableMapOf(
        "admin" to JsonObject()
            .put("username", "admin")
            .put("password", "admin123") // In production, use hashed passwords
            .put("role", "admin"),
        "user" to JsonObject()
            .put("username", "user")
            .put("password", "user123")
            .put("role", "user")
    )

    /**
     * Sets up the authentication API routes.
     */
    fun setupRoutes(router: Router) {
        logger.info("Setting up authentication API routes...")

        // Authentication endpoints
        router.post("/auth/login").handler(this::login)
        router.post("/auth/logout").handler(this::logout)
        router.get("/auth/me").handler(this::getCurrentUser)
        router.post("/auth/register").handler(this::register)
        router.put("/auth/change-password").handler(this::changePassword)
    }

    /**
     * Handles user login.
     */
    private fun login(context: RoutingContext) {
        try {
            val body = context.body().asJsonObject()

            // Validate required fields
            if (!body.containsKey("username") || !body.containsKey("password")) {
                context.response()
                    .setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Missing required fields: username, password")
                        .encode()
                    )
                return
            }

            val username = body.getString("username")
            val password = body.getString("password")

            // Check if user exists
            val user = users[username]

            if (user == null) {
                context.response()
                    .setStatusCode(401)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Invalid username or password")
                        .encode()
                    )
                return
            }

            // Check password
            if (user.getString("password") != password) {
                context.response()
                    .setStatusCode(401)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Invalid username or password")
                        .encode()
                    )
                return
            }

            // Generate JWT token
            val claims = JsonObject()
                .put("sub", username)
                .put("role", user.getString("role"))
                .put("iat", Instant.now().epochSecond)
                .put("exp", Instant.now().plusSeconds(3600).epochSecond) // 1 hour expiration
                .put("jti", UUID.randomUUID().toString())

            val token = jwtAuth.generateToken(claims, JWTOptions().setExpiresInSeconds(3600))

            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("token", token)
                    .put("user", JsonObject()
                        .put("username", username)
                        .put("role", user.getString("role"))
                    )
                    .encode()
                )
        } catch (e: Exception) {
            logger.error("Error during login", e)

            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to login: ${e.message}")
                    .encode()
                )
        }
    }

    /**
     * Handles user logout.
     */
    private fun logout(context: RoutingContext) {
        // In a stateless JWT authentication, logout is handled client-side
        // by removing the token from storage
        context.response()
            .putHeader("Content-Type", "application/json")
            .end(JsonObject()
                .put("success", true)
                .put("message", "Logged out successfully")
                .encode()
            )
    }

    /**
     * Gets the current user.
     */
    private fun getCurrentUser(context: RoutingContext) {
        try {
            val user = context.user()

            if (user == null) {
                context.response()
                    .setStatusCode(401)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Not authenticated")
                        .encode()
                    )
                return
            }

            user.principal().let { principal ->
                val username = principal.getString("sub")
                val role = principal.getString("role")

                context.response()
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("user", JsonObject()
                            .put("username", username)
                            .put("role", role)
                        )
                        .encode()
                    )
            }
        } catch (e: Exception) {
            logger.error("Error getting current user", e)

            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to get current user: ${e.message}")
                    .encode()
                )
        }
    }

    /**
     * Handles user registration.
     */
    private fun register(context: RoutingContext) {
        try {
            val body = context.body().asJsonObject()

            // Validate required fields
            if (!body.containsKey("username") || !body.containsKey("password")) {
                context.response()
                    .setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Missing required fields: username, password")
                        .encode()
                    )
                return
            }

            val username = body.getString("username")
            val password = body.getString("password")

            // Check if user already exists
            if (users.containsKey(username)) {
                context.response()
                    .setStatusCode(409)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Username already exists")
                        .encode()
                    )
                return
            }

            // Create new user
            val user = JsonObject()
                .put("username", username)
                .put("password", password) // In production, use hashed passwords
                .put("role", "user") // Default role

            users[username] = user

            context.response()
                .setStatusCode(201)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("message", "User registered successfully")
                    .encode()
                )
        } catch (e: Exception) {
            logger.error("Error during registration", e)

            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to register: ${e.message}")
                    .encode()
                )
        }
    }

    /**
     * Handles password change.
     */
    private fun changePassword(context: RoutingContext) {
        try {
            val user = context.user()

            if (user == null) {
                context.response()
                    .setStatusCode(401)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Not authenticated")
                        .encode()
                    )
                return
            }

            val body = context.body().asJsonObject()

            // Validate required fields
            if (!body.containsKey("currentPassword") || !body.containsKey("newPassword")) {
                context.response()
                    .setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Missing required fields: currentPassword, newPassword")
                        .encode()
                    )
                return
            }

            val currentPassword = body.getString("currentPassword")
            val newPassword = body.getString("newPassword")

            user.principal().let { principal ->
                val username = principal.getString("sub")

                // Check if user exists
                val userObj = users[username]

                if (userObj == null) {
                    context.response()
                        .setStatusCode(404)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("success", false)
                            .put("error", "User not found")
                            .encode()
                        )
                    return
                }

                // Check current password
                if (userObj.getString("password") != currentPassword) {
                    context.response()
                        .setStatusCode(401)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("success", false)
                            .put("error", "Current password is incorrect")
                            .encode()
                        )
                    return
                }

                // Update password
                userObj.put("password", newPassword)
                users[username] = userObj

                context.response()
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", true)
                        .put("message", "Password changed successfully")
                        .encode()
                    )
            }
        } catch (e: Exception) {
            logger.error("Error changing password", e)

            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to change password: ${e.message}")
                    .encode()
                )
        }
    }
}
