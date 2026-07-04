package io.averkhogliad.ai.challenge.week4.cli.domain.rag.model

/**
 * Причина fallback-а на обычный LLM-запрос.
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
    LLM_ERROR
}

/**
 * Результат RAG-запроса.
 *
 * Содержит ответ LLM, список использованных источников (релевантных чанков),
 * а также флаги, отражающие режим и факт fallback-а.
 *
 * @property answer текст ответа от LLM
 * @property sources список релевантных чанков, использованных как контекст (может быть пустым)
 * @property ragEnabled был ли RAG включен на момент запроса
 * @property fallbackToPlain произошёл ли fallback на обычный LLM-запрос
 * @property fallbackReason причина fallback-а, если [fallbackToPlain] = true
 * @property llmError сообщение об ошибке LLM, если она произошла
 */
data class RagAnswer(
    val answer: String,
    val sources: List<RelevantChunk> = emptyList(),
    val ragEnabled: Boolean = false,
    val fallbackToPlain: Boolean = false,
    val fallbackReason: FallbackReason? = null,
    val llmError: String? = null,
    val searchContext: SearchContext? = null
) {
    val isLlmError: Boolean get() = llmError != null
}
