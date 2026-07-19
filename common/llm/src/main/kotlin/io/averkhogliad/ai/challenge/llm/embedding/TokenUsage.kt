package io.averkhogliad.ai.challenge.llm.embedding

/**
 * Статистика использования токенов при генерации эмбеддингов.
 *
 * @property promptTokens количество токенов во входном тексте
 * @property totalTokens общее количество использованных токенов
 */
data class TokenUsage(
    val promptTokens: Int,
    val totalTokens: Int,
)
