package io.averkhogliad.ai.challenge.llm.embedding

/**
 * Векторное представление одного текста.
 *
 * @property text исходный текст
 * @property vector вектор эмбеддинга
 * @property index позиция в исходном запросе
 */
data class LlmEmbedding(
    val text: String,
    val vector: FloatArray,
    val index: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LlmEmbedding) return false
        return index == other.index && text == other.text && vector.contentEquals(other.vector)
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + vector.contentHashCode()
        result = 31 * result + index
        return result
    }
}
