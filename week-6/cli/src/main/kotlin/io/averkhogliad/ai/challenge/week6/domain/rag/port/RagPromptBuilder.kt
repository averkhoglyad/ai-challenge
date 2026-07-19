package io.averkhogliad.ai.challenge.week6.domain.rag.port

import io.averkhogliad.ai.challenge.week6.domain.rag.model.RelevantChunk

/**
 * Порт для сборки RAG-промпта.
 *
 * Определяет контракт между application-слоем (оркестрация RAG) и
 * infrastructure-слоем (форматирование промпта с контекстом).
 *
 * Разные реализации могут использовать разные форматы промптов:
 * простая конкатенация, шаблоны с ролями (system/user), few-shot примеры.
 */
interface RagPromptBuilder {

    /**
     * Собирает полный текст промпта, включающий контекст из релевантных чанков.
     *
     * @param question исходный вопрос пользователя
     * @param context список релевантных чанков (уже отфильтрованных и отсортированных)
     * @return полный текст промпта, готовый к отправке в LLM
     */
    fun build(question: String, context: List<RelevantChunk>): String
}
