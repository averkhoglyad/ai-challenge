package io.averkhogliad.ai.challenge.week4.cli.domain.rag.model

/**
 * Причина fallback-а на обычный LLM-запрос или режима «не знаю».
 *
 * Позволяет CLI-слою показывать правильное предупреждение пользователю.
 */
enum class FallbackReason {
    /** RAG выключен — обычный LLM */
    RAG_DISABLED,

    /** RAG включён, но активный индекс не выбран */
    NO_ACTIVE_INDEX,

    /** Индекс есть, но поиск не нашёл чанков выше threshold */
    EMPTY_SEARCH,

    /** Ошибка при генерации embedding для вопроса */
    EMBEDDING_ERROR,

    /** Ошибка при векторном поиске */
    SEARCH_ERROR,

    /** Ошибка при отправке в LLM */
    LLM_ERROR,

    // ──── Новые причины (Task 4: Anti-hallucination) ────

    /** Релевантность контекста ниже порога — режим «не знаю» */
    INSUFFICIENT_RELEVANCE,

    /** Ошибка парсинга структурированного ответа LLM */
    PARSE_ERROR,

    /** Ошибка валидации ответа (отсутствуют цитаты или ссылки) */
    VALIDATION_ERROR
}

/**
 * Результат RAG-запроса.
 *
 * Содержит ответ LLM, список использованных источников (релевантных чанков),
 * а также флаги, отражающие режим и факт fallback-а.
 *
 * ## Task 4: Anti-hallucination
 * Добавлены поля для цитат ([citations]) и режима «не знаю»
 * ([isInsufficientContext], [clarificationRequest], [maxRelevanceScore], [requiredThreshold]).
 *
 * @property answer текст ответа от LLM
 * @property sources список релевантных чанков, использованных как контекст (может быть пустым)
 * @property ragEnabled был ли RAG включен на момент запроса
 * @property fallbackToPlain произошёл ли fallback на обычный LLM-запрос
 * @property fallbackReason причина fallback-а, если [fallbackToPlain] = true
 * @property llmError сообщение об ошибке LLM, если она произошла
 * @property searchContext контекст поиска со статистикой (если использовался SearchPipeline)
 * @property citations список цитат с текстом фрагментов (Task 4)
 * @property isInsufficientContext флаг режима «не знаю» при недостаточной релевантности (Task 4)
 * @property clarificationRequest просьба уточнить вопрос (Task 4)
 * @property maxRelevanceScore максимальный показатель релевантности (Task 4)
 * @property requiredThreshold требуемый порог релевантности (Task 4)
 * @property validationErrors ошибки валидации ответа (Task 4)
 */
data class RagAnswer(
    val answer: String,
    val sources: List<RelevantChunk> = emptyList(),
    val ragEnabled: Boolean = false,
    val fallbackToPlain: Boolean = false,
    val fallbackReason: FallbackReason? = null,
    val llmError: String? = null,
    val searchContext: SearchContext? = null,
    // ──── Task 4: Anti-hallucination поля ────
    val citations: List<Citation> = emptyList(),
    val isInsufficientContext: Boolean = false,
    val clarificationRequest: String? = null,
    val maxRelevanceScore: Float? = null,
    val requiredThreshold: Float? = null,
    val validationErrors: List<String> = emptyList()
) {
    val isLlmError: Boolean get() = llmError != null
}
