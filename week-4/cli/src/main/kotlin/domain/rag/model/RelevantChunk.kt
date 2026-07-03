package io.averkhogliad.ai.challenge.week4.cli.domain.rag.model

import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.Chunk

/**
 * Релевантный чанк с оценкой близости к поисковому запросу.
 *
 * Обёртка над [Chunk], расширенная метрикой [score] — косинусным сходством
 * между embedding запроса и embedding чанка.
 *
 * ## Семантика равенства
 * equals/hashCode учитывают только [Chunk.id] и [score] —
 * два объекта с одинаковым id чанка и score считаются равными,
 * даже если содержимое чанков различается.
 *
 * @property chunk исходный чанк из индекса
 * @property score оценка релевантности (косинусное сходство) в диапазоне [-1.0, 1.0]
 */
class RelevantChunk(
    val chunk: Chunk,
    val score: Float
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RelevantChunk) return false
        return chunk.id == other.chunk.id && score == other.score
    }

    override fun hashCode(): Int {
        var result = chunk.id.hashCode()
        result = 31 * result + score.hashCode()
        return result
    }

    override fun toString(): String = "RelevantChunk(chunk=${chunk.id}, score=$score)"

    fun copy(
        chunk: Chunk = this.chunk,
        score: Float = this.score
    ): RelevantChunk = RelevantChunk(chunk, score)
}
