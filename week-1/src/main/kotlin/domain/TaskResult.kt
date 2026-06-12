package io.averkhogliad.ai.challenge.week1.domain

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
     */
    data class Success(
        val content: String,
        val metadata: Map<String, Any> = emptyMap()
    ) : TaskResult

    /**
     * Ошибочный результат.
     * @property message сообщение об ошибке
     * @property cause причина ошибки (опционально)
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : TaskResult

    /**
     * Частичный результат (промежуточная выдача).
     * @property content фрагмент контента
     * @property progress прогресс от 0.0 до 1.0
     */
    data class Partial(
        val content: String,
        val progress: Double
    ) : TaskResult
}
