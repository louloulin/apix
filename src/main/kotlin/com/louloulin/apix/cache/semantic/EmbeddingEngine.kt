package com.louloulin.apix.cache.semantic

import io.vertx.core.Future

/**
 * 嵌入式引擎接口，负责计算文本的嵌入向量
 */
interface EmbeddingEngine {
    /**
     * 计算文本的嵌入向量
     * @param text 输入文本
     * @return 嵌入向量的 Future
     */
    fun embed(text: String): Future<FloatArray>
    
    /**
     * 计算多个文本的嵌入向量
     * @param texts 输入文本列表
     * @return 嵌入向量列表的 Future
     */
    fun embedBatch(texts: List<String>): Future<List<FloatArray>>
    
    /**
     * 计算两个嵌入向量之间的相似度
     * @param embedding1 第一个嵌入向量
     * @param embedding2 第二个嵌入向量
     * @return 相似度（0-1 之间的值，1 表示完全相同）
     */
    fun similarity(embedding1: FloatArray, embedding2: FloatArray): Float
    
    /**
     * 计算两个文本之间的相似度
     * @param text1 第一个文本
     * @param text2 第二个文本
     * @return 相似度的 Future
     */
    fun textSimilarity(text1: String, text2: String): Future<Float>
    
    /**
     * 关闭嵌入式引擎
     * @return 操作结果的 Future
     */
    fun close(): Future<Void>
}
