package io.averkhogliad.ai.challenge.week3.cli.domain

import io.averkhogliad.ai.challenge.week3.cli.domain.model.DomainToolCall
import io.averkhogliad.ai.challenge.week3.cli.domain.telemetry.TokenUsage

/**
 * Результат выполнения задачи.
 *
 * [sealed interface](https://kotlinlang.org/docs/sealed-classes.html) гарантирует
 * исчерпывающую обработку всех вариантов в `when`-выражениях без `else`.
 *
 * Варианты:
 * - [Success] — задача выполнена успешно, есть контент и метаданные
 * - [Error] — задача завершилась с ошибкой
 * - [Partial] — частичный результат (например, потоковая выдача)
 */
sealed interface TaskResult {
    /**
     * Успешный результат выполнения задачи.
     * @property content текстовый вывод задачи
     * @property metadata дополнительные метаданные (например, usage, timing)
     * @property tokenUsage использование токенов для этого вызова (извлекается из metadata)
     */
    data class Success(
        val content: String,
        val metadata: Map<String, Any> = emptyMap(),
        val tokenUsage: TokenUsage? = TokenUsage.fromMetadata(metadata),
        val toolCalls: List<DomainToolCall>? = null
    ) : TaskResult

    /**
     * Ошибочный результат.
     * @property message сообщение об ошибке
     * @property cause причина ошибки (опционально)
     * @property tokenUsage использование токенов (опционально, если известны при ошибке)
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null,
        val tokenUsage: TokenUsage? = null
    ) : TaskResult

    /**
     * Частичный результат (промежуточная выдача).
     * @property content фрагмент контента
     * @property progress прогресс от 0.0 до 1.0
     * @property tokenUsage использование токенов (опционально, если известны)
     */
    data class Partial(
        val content: String,
        val progress: Double,
        val tokenUsage: TokenUsage? = null
    ) : TaskResult
}
