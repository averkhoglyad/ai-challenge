package io.averkhogliad.ai.challenge.week4.cli.infrastructure.rag.search

import kotlin.math.sqrt

/**
 * Вычисление косинусного сходства между двумя векторами.
 *
 * @return значение в диапазоне [-1.0, 1.0], где 1.0 — идентичные направления,
 *         0.0 — ортогональные, -1.0 — противоположные.
 *         Для нулевых векторов возвращает 0.0, для векторов разной размерности — 0.0.
 */
fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    if (a.size != b.size) return 0.0f

    var dotProduct = 0.0f
    var normA = 0.0f
    var normB = 0.0f

    for (i in a.indices) {
        dotProduct += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }

    if (normA == 0.0f || normB == 0.0f) return 0.0f

    return dotProduct / (sqrt(normA) * sqrt(normB))
}
