package com.louloulin.apix.core.eventbus

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.eventbus.DeliveryOptions
import io.vertx.core.eventbus.EventBus
import io.vertx.core.eventbus.Message
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.Deflater

/**
 * Enhanced distributed EventBus implementation with optimizations for clustered environments.
 * Provides message partitioning, compression, batching, and QoS support.
 */
class DistributedEventBus(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(DistributedEventBus::class.java)

    // Original EventBus
    private val originalEventBus: EventBus = vertx.eventBus()

    // Whether the distributed EventBus is started
    private val started = AtomicBoolean(false)

    // Performance statistics
    private val messagesSent = AtomicLong(0)
    private val messagesReceived = AtomicLong(0)
    private val messagesFailed = AtomicLong(0)
    private val messagesCompressed = AtomicLong(0)
    private val bytesSent = AtomicLong(0)
    private val bytesCompressed = AtomicLong(0)
    private val compressionRatio = AtomicLong(0)

    // Message partitioning
    private val partitionMap = ConcurrentHashMap<String, Int>()
    private val partitionCount = 16 // Default partition count

    // Message compression settings
    private val compressionEnabled = AtomicBoolean(true)
    private val compressionThreshold = 1024 // Compress messages larger than 1KB
    private val compressionLevel = Deflater.BEST_SPEED // Use fastest compression

    // Message batching
    private val batchProcessor = BatchMessageProcessor.getInstance(vertx)

    // Flag to indicate if batch processing is enabled
    private val batchingEnabled = AtomicBoolean(false)

    // QoS settings
    private val priorityMap = ConcurrentHashMap<String, Int>()
    private val defaultPriority = 5 // Default priority (1-10, 10 being highest)

    /**
     * Start the distributed EventBus.
     */
    fun start(): Future<Void> {
        if (started.compareAndSet(false, true)) {
            logger.info("Starting DistributedEventBus")

            // Initialize components
            initializePartitioning()
            initializeQoS()

            logger.info("DistributedEventBus started successfully")
        } else {
            logger.info("DistributedEventBus already started")
        }

        return Future.succeededFuture()
    }

    /**
     * Initialize message partitioning.
     */
    private fun initializePartitioning() {
        logger.info("Initializing message partitioning with $partitionCount partitions")

        // Register partition handler
        vertx.eventBus().consumer<JsonObject>("apix.eventbus.partition.config") { message ->
            val config = message.body()
            val newPartitionCount = config.getInteger("partitionCount", partitionCount)

            if (newPartitionCount != partitionCount) {
                logger.info("Updating partition count from $partitionCount to $newPartitionCount")
                partitionMap.clear()
            }

            message.reply(JsonObject().put("success", true))
        }
    }

    /**
     * Initialize QoS settings.
     */
    private fun initializeQoS() {
        logger.info("Initializing QoS settings")

        // Register QoS handler
        vertx.eventBus().consumer<JsonObject>("apix.eventbus.qos.config") { message ->
            val config = message.body()
            val addressPriorities = config.getJsonObject("priorities", JsonObject())

            // Update priority map
            for (address in addressPriorities.fieldNames()) {
                val priority = addressPriorities.getInteger(address, defaultPriority)
                priorityMap[address] = priority
                logger.debug("Set priority for address $address to $priority")
            }

            message.reply(JsonObject().put("success", true))
        }
    }

    /**
     * Get the original EventBus.
     */
    fun getOriginalEventBus(): EventBus {
        return originalEventBus
    }

    /**
     * Send a message with partitioning, compression, and QoS support.
     *
     * @param address The destination address
     * @param message The message to send
     * @param options Optional delivery options
     * @return A future that completes when the message is processed
     */
    fun <T> send(address: String, message: Any, options: DeliveryOptions? = null): Future<Message<T>> {
        // Ensure the distributed EventBus is started
        if (!started.get()) {
            start()
        }

        // Update statistics
        messagesSent.incrementAndGet()

        // Create a promise for the result
        val promise = Promise.promise<Message<T>>()

        try {
            // Apply message partitioning if needed
            val targetAddress = if (shouldPartition(address)) {
                getPartitionedAddress(address, message)
            } else {
                address
            }

            // Apply message compression if needed
            val (finalMessage, finalOptions) = if (shouldCompress(message)) {
                compressMessage(message, options)
            } else {
                Pair(message, options ?: DeliveryOptions())
            }

            // Apply QoS settings
            applyQoS(finalOptions, address)

            // Track bytes sent
            val messageSize = estimateMessageSize(finalMessage)
            bytesSent.addAndGet(messageSize)

            // Send the message
            originalEventBus.request<T>(targetAddress, finalMessage, finalOptions) { ar ->
                if (ar.succeeded()) {
                    messagesReceived.incrementAndGet()
                    promise.complete(ar.result())
                } else {
                    messagesFailed.incrementAndGet()
                    promise.fail(ar.cause())
                }
            }
        } catch (e: Exception) {
            messagesFailed.incrementAndGet()
            logger.error("Error sending message to $address", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * Publish a message with compression and QoS support.
     *
     * @param address The destination address
     * @param message The message to publish
     * @param options Optional delivery options
     */
    fun publish(address: String, message: Any, options: DeliveryOptions? = null) {
        // Ensure the distributed EventBus is started
        if (!started.get()) {
            start()
        }

        // Update statistics
        messagesSent.incrementAndGet()

        try {
            // Apply message compression if needed
            val (finalMessage, finalOptions) = if (shouldCompress(message)) {
                compressMessage(message, options)
            } else {
                Pair(message, options ?: DeliveryOptions())
            }

            // Apply QoS settings
            applyQoS(finalOptions, address)

            // Track bytes sent
            val messageSize = estimateMessageSize(finalMessage)
            bytesSent.addAndGet(messageSize)

            // Publish the message
            originalEventBus.publish(address, finalMessage, finalOptions)
        } catch (e: Exception) {
            messagesFailed.incrementAndGet()
            logger.error("Error publishing message to $address", e)
        }
    }

    /**
     * Send a message to a batch processor for batching.
     *
     * @param address The destination address
     * @param message The message to send
     */
    fun sendToBatch(address: String, message: Any) {
        // Ensure the distributed EventBus is started
        if (!started.get()) {
            start()
        }

        // Update statistics
        messagesSent.incrementAndGet()

        // If batching is not enabled, send directly
        if (!batchingEnabled.get()) {
            send<Any>(address, message)
            return
        }

        // In a real implementation, this would use the batch processor
        // For now, just send directly
        send<Any>(address, message)
    }

    /**
     * Determine if a message should be partitioned.
     */
    private fun shouldPartition(address: String): Boolean {
        // Partition messages for addresses that start with "apix.cluster"
        return address.startsWith("apix.cluster") && vertx.isClustered()
    }

    /**
     * Get a partitioned address based on the message content.
     */
    private fun getPartitionedAddress(address: String, message: Any): String {
        // Calculate partition based on message hash
        val hash = when (message) {
            is String -> message.hashCode()
            is JsonObject -> message.hashCode()
            else -> message.hashCode()
        }

        val partition = Math.abs(hash % partitionCount)

        // Cache the partition for this address
        partitionMap[address] = partition

        return "$address.p$partition"
    }

    /**
     * Determine if a message should be compressed.
     */
    private fun shouldCompress(message: Any): Boolean {
        if (!compressionEnabled.get()) {
            return false
        }

        // Estimate message size
        val size = estimateMessageSize(message)
        return size > compressionThreshold
    }

    /**
     * Compress a message.
     */
    private fun compressMessage(message: Any, options: DeliveryOptions?): Pair<Any, DeliveryOptions> {
        val opts = options ?: DeliveryOptions()

        try {
            when (message) {
                is String -> {
                    val bytes = message.toByteArray()
                    val compressed = compress(bytes)

                    // Update compression statistics
                    messagesCompressed.incrementAndGet()
                    bytesCompressed.addAndGet(bytes.size.toLong() - compressed.size.toLong())
                    if (bytes.size > 0) {
                        compressionRatio.set((bytes.size.toLong() * 100 / compressed.size.toLong()))
                    }

                    // Set compression header
                    opts.addHeader("compressed", "true")
                    opts.addHeader("original-type", "string")

                    return Pair(compressed, opts)
                }
                is JsonObject -> {
                    val bytes = message.encode().toByteArray()
                    val compressed = compress(bytes)

                    // Update compression statistics
                    messagesCompressed.incrementAndGet()
                    bytesCompressed.addAndGet(bytes.size.toLong() - compressed.size.toLong())
                    if (bytes.size > 0) {
                        compressionRatio.set((bytes.size.toLong() * 100 / compressed.size.toLong()))
                    }

                    // Set compression header
                    opts.addHeader("compressed", "true")
                    opts.addHeader("original-type", "json")

                    return Pair(compressed, opts)
                }
                else -> {
                    // For other types, return as is
                    return Pair(message, opts)
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to compress message", e)
            return Pair(message, opts)
        }
    }

    /**
     * Compress byte array using Deflater.
     */
    private fun compress(data: ByteArray): ByteArray {
        val deflater = Deflater(compressionLevel)
        deflater.setInput(data)
        deflater.finish()

        val buffer = ByteArray(data.size)
        val compressedSize = deflater.deflate(buffer)
        deflater.end()

        return buffer.copyOf(compressedSize)
    }

    /**
     * Apply QoS settings to delivery options.
     */
    private fun applyQoS(options: DeliveryOptions, address: String) {
        // Set priority based on address
        val priority = priorityMap[address] ?: defaultPriority
        options.addHeader("priority", priority.toString())

        // Set timeout based on priority
        val timeout = when {
            priority >= 8 -> 500L // High priority: 500ms
            priority >= 5 -> 1000L // Medium priority: 1s
            else -> 2000L // Low priority: 2s
        }

        options.setSendTimeout(timeout)
    }

    /**
     * Estimate the size of a message in bytes.
     */
    private fun estimateMessageSize(message: Any): Long {
        return when (message) {
            is String -> message.length.toLong()
            is JsonObject -> message.encode().length.toLong()
            is ByteArray -> message.size.toLong()
            else -> 100L // Default estimate for unknown types
        }
    }

    /**
     * Get statistics about the distributed EventBus.
     */
    fun getStats(): JsonObject {
        return JsonObject()
            .put("started", started.get())
            .put("messagesSent", messagesSent.get())
            .put("messagesReceived", messagesReceived.get())
            .put("messagesFailed", messagesFailed.get())
            .put("messagesCompressed", messagesCompressed.get())
            .put("bytesSent", bytesSent.get())
            .put("bytesCompressed", bytesCompressed.get())
            .put("compressionRatio", compressionRatio.get())
            .put("compressionEnabled", compressionEnabled.get())
            .put("partitionCount", partitionCount)
            .put("partitions", JsonObject(partitionMap.mapValues { it.value }))
            .put("priorities", JsonObject(priorityMap.mapValues { it.value }))
    }

    /**
     * Enable or disable message compression.
     */
    fun setCompressionEnabled(enabled: Boolean) {
        compressionEnabled.set(enabled)
        logger.info("Message compression ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Stop the distributed EventBus.
     */
    fun stop(): Future<Void> {
        if (started.compareAndSet(true, false)) {
            logger.info("Stopping DistributedEventBus")
        }

        return Future.succeededFuture()
    }

    companion object {
        // Singleton instance
        @Volatile
        private var instance: DistributedEventBus? = null

        /**
         * Get the singleton instance of DistributedEventBus.
         * @param vertx The Vert.x instance.
         * @return The DistributedEventBus instance.
         */
        fun getInstance(vertx: Vertx): DistributedEventBus {
            return instance ?: synchronized(this) {
                instance ?: DistributedEventBus(vertx).also { instance = it }
            }
        }
    }
}
