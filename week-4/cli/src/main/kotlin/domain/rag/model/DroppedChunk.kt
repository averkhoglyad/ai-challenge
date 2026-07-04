package io.averkhogliad.ai.challenge.week4.cli.domain.rag.model

/**
 * Отброшенный чанк с причиной отсева.
 *
 * @property chunk чанк, не прошедший фильтрацию
 * @property reason причина, по которой чанк был отброшен
 */
data class DroppedChunk(
    val chunk: RelevantChunk,
    val reason: DropReason
)

/**
 * Причина отсева чанка.
 *
 * Sealed interface для exhaustiveness в when-выражениях.
 */
sealed interface DropReason {
    /** Отброшен threshold-фильтром (score ниже порога) */
    data class BelowThreshold(val threshold: Float) : DropReason

    /** Отброшен LLM-реранкером (низкий rerank-score) */
    data class LowRerankScore(val score: Float, val minScore: Float) : DropReason

    /** Отброшен из-за лимита topK_final (не вошёл в top-K) */
    data object TopKLimit : DropReason
}
