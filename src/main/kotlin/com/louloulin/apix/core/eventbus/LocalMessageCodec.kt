package com.louloulin.apix.core.eventbus

import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.MessageCodec

/**
 * 本地消息编解码器，用于优化同一JVM内的EventBus通信。
 * 在本地模式下，直接传递对象引用，避免序列化/反序列化开销。
 * 在集群模式下，仍然需要序列化/反序列化，但这个编解码器不处理这种情况。
 *
 * @param T 消息类型
 * @property clazz 消息类的Class对象
 */
class LocalMessageCodec<T>(private val clazz: Class<T>) : MessageCodec<T, T> {

    /**
     * 编码到Wire，集群模式才会调用，本地模式不会调用
     */
    override fun encodeToWire(buffer: Buffer, s: T) {
        // 集群模式才会调用，本地模式不会调用
        // 这里简单实现，但实际上不会被调用
        buffer.appendString(s.toString())
    }

    /**
     * 从Wire解码，集群模式才会调用，本地模式不会调用
     */
    override fun decodeFromWire(pos: Int, buffer: Buffer): T {
        // 集群模式才会调用，本地模式不会调用
        // 这里简单实现，但实际上不会被调用
        // 由于无法正确反序列化，所以返回null，但实际上这个方法不会被调用
        return null as T
    }

    /**
     * 转换对象，本地模式下直接返回原对象，避免序列化/反序列化
     */
    override fun transform(s: T): T {
        // 本地模式下直接返回原对象，避免序列化/反序列化
        return s
    }

    /**
     * 编解码器名称
     */
    override fun name(): String {
        return "local.${clazz.simpleName}"
    }

    /**
     * 系统编解码器ID
     */
    override fun systemCodecID(): Byte {
        return -1
    }
}
