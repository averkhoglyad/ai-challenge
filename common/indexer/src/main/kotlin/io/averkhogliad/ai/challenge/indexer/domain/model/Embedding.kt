package io.averkhogliad.ai.challenge.indexer.domain.model

import java.util.*

data class Embedding(
    val chunkId: UUID,
    val vector: FloatArray,
    val model: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Embedding) return false
        return chunkId == other.chunkId && model == other.model && vector.contentEquals(other.vector)
    }

    override fun hashCode(): Int {
        var result = chunkId.hashCode()
        result = 31 * result + vector.contentHashCode()
        result = 31 * result + model.hashCode()
        return result
    }
}
