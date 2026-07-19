package io.averkhogliad.ai.challenge.week6.domain.indexer.model

import java.util.*

/**
 * Векторное представление (эмбеддинг) текстового чанка.
 *
 * @property chunkId ссылка на чанк
 * @property vector вектор эмбеддинга (размерность зависит от модели)
 * @property model название модели, сгенерировавшей эмбеддинг
 */
data class Embedding(
    val chunkId: UUID,
    val vector: FloatArray,
    val model: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Embedding) return false
        return chunkId == other.chunkId &&
                vector.contentEquals(other.vector) &&
                model == other.model
    }

    override fun hashCode(): Int {
        var result = chunkId.hashCode()
        result = 31 * result + vector.contentHashCode()
        result = 31 * result + model.hashCode()
        return result
    }
}
