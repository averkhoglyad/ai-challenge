package io.averkhogliad.ai.challenge.week4.cli.application.rag

import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RelevanceCheckResult
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RelevantChunk

/**
 * Проверяет, достаточна ли релевантность чанков для генерации ответа.
 *
 * Сравнивает максимальный показатель релевантности ([RelevantChunk.score])
 * с заданным порогом [threshold]. При [maxScore] >= [threshold] возвращает
 * [RelevanceCheckResult.Sufficient], иначе — [RelevanceCheckResult.Insufficient].
 * Пустой список чанков всегда даёт Insufficient с [maxScore] = 0.
 */
class RelevanceChecker {

    /**
     * Проверяет релевантность списка чанков.
     *
     * @param chunks список релевантных чанков (может быть пустым)
     * @param threshold порог релевантности (0.0..1.0)
     * @return [RelevanceCheckResult.Sufficient] если maxScore >= threshold,
     *         [RelevanceCheckResult.Insufficient] в противном случае
     */
    fun check(chunks: List<RelevantChunk>, threshold: Float): RelevanceCheckResult {
        if (chunks.isEmpty()) {
            return RelevanceCheckResult.Insufficient(
                maxScore = 0f,
                threshold = threshold,
                chunks = chunks
            )
        }

        val maxScore = chunks.maxOf { it.score }
        val averageScore = chunks.map { it.score }.average().toFloat()

        return if (maxScore >= threshold) {
            RelevanceCheckResult.Sufficient(
                chunks = chunks,
                maxScore = maxScore,
                averageScore = averageScore
            )
        } else {
            RelevanceCheckResult.Insufficient(
                maxScore = maxScore,
                threshold = threshold,
                chunks = chunks
            )
        }
    }
}
