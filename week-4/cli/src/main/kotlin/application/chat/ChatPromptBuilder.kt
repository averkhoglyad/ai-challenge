package io.averkhogliad.ai.challenge.week4.cli.application.chat

import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatMessage
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskState
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RelevantChunk
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.port.RagPromptBuilder

/**
 * Формирует итоговый промпт для LLM, объединяя четыре блока:
 * 1. Task State (цель, термины, ограничения, факты)
 * 2. History (последние N сообщений)
 * 3. RAG Context (через [RagPromptBuilder])
 * 4. Current Question
 *
 * ## Архитектурная роль
 * - **Application Layer** — оркестрация, формирование промпта
 * - **Не зависит** от UI и infrastructure
 * - **Зависит только** от domain-портов ([RagPromptBuilder]) и domain-моделей
 *
 * При превышении лимита токенов обрезает историю (graceful degradation).
 *
 * @property citationPromptBuilder базовый построитель RAG-промпта (CitationAwarePromptBuilder)
 * @property config конфигурация чата
 */
class ChatPromptBuilder(
    private val citationPromptBuilder: RagPromptBuilder,
    private val config: ChatConfig
) {

    companion object {
        /** Приблизительное число символов на токен для эвристической оценки длины. */
        private const val CHARS_PER_TOKEN = 4

        /** Ориентировочный лимит токенов для промпта. */
        private const val MAX_PROMPT_TOKENS = 6000
    }

    /**
     * Собирает полный промпт с блоками TaskState, History, RAG Context и Question.
     *
     * @param taskState текущее состояние памяти задачи
     * @param history история сообщений для включения в промпт
     * @param ragContext релевантные чанки из RAG-поиска (может быть пустым)
     * @param question вопрос пользователя
     * @return итоговый текст промпта
     */
    fun build(
        taskState: TaskState,
        history: List<ChatMessage>,
        ragContext: List<RelevantChunk>,
        question: String
    ): String {
        val parts = mutableListOf<String>()

        // 1. Task State
        parts.add(buildTaskStateBlock(taskState))

        // 2. History (с обрезкой при превышении лимита)
        parts.add(buildHistoryBlock(history))

        // 3. RAG Context
        if (ragContext.isNotEmpty()) {
            parts.add(citationPromptBuilder.build(question, ragContext))
        }

        // 4. Current Question
        parts.add("ВОПРОС: $question")

        return trimToTokenLimit(parts.joinToString("\n\n"))
    }

    /**
     * Строит блок состояния задачи.
     */
    private fun buildTaskStateBlock(taskState: TaskState): String {
        val sb = StringBuilder()
        sb.appendLine("=== КОНТЕКСТ ЗАДАЧИ ===")

        if (taskState.goal != null) {
            sb.appendLine("Цель: ${taskState.goal}")
        }

        if (taskState.definedTerms.isNotEmpty()) {
            sb.appendLine("Термины:")
            taskState.definedTerms.forEach { (name, definition) ->
                sb.appendLine("  - $name: $definition")
            }
        }

        if (taskState.constraints.isNotEmpty()) {
            sb.appendLine("Ограничения:")
            taskState.constraints.forEach { constraint ->
                sb.appendLine("  - $constraint")
            }
        }

        if (taskState.clarifiedFacts.isNotEmpty()) {
            sb.appendLine("Уточнённые факты:")
            taskState.clarifiedFacts.forEach { fact ->
                sb.appendLine("  - $fact")
            }
        }

        return sb.toString().trimEnd()
    }

    /**
     * Строит блок истории диалога.
     * Если блок слишком длинный — обрезает с начала (оставляет последние сообщения).
     */
    private fun buildHistoryBlock(history: List<ChatMessage>): String {
        if (history.isEmpty()) return "=== ИСТОРИЯ ===\n(пусто)"

        val lines = mutableListOf<String>()
        lines.add("=== ИСТОРИЯ ===")

        for (message in history) {
            when (message) {
                is ChatMessage.User -> lines.add("User: ${message.text}")
                is ChatMessage.Assistant -> lines.add("Assistant: ${message.text}")
                is ChatMessage.System -> lines.add("System: ${message.text}")
            }
        }

        val fullBlock = lines.joinToString("\n")
        return trimHistoryBlock(fullBlock)
    }

    /**
     * Обрезает блок истории, оставляя последние сообщения,
     * чтобы уложиться в лимит токенов.
     */
    private fun trimHistoryBlock(block: String): String {
        val estimatedTokens = block.length / CHARS_PER_TOKEN
        if (estimatedTokens <= MAX_PROMPT_TOKENS / 2) return block

        // Обрезаем с начала: оставляем последнюю треть сообщений
        val lines = block.lines()
        val header = lines.first() // "=== ИСТОРИЯ ==="
        val messageLines = lines.drop(1)

        val keepCount = (messageLines.size / 3).coerceAtLeast(1)
        val trimmed = messageLines.takeLast(keepCount)

        return (listOf("$header\n(обрезано до последних $keepCount сообщений)") + trimmed)
            .joinToString("\n")
    }

    /**
     * Обрезает итоговый промпт до лимита токенов,
     * оставляя начало (контекст задачи) и конец (вопрос).
     */
    private fun trimToTokenLimit(prompt: String): String {
        val estimatedTokens = prompt.length / CHARS_PER_TOKEN
        if (estimatedTokens <= MAX_PROMPT_TOKENS) return prompt

        // Сохраняем первые ~30% и последние ~70%
        val keepHeadChars = (MAX_PROMPT_TOKENS * CHARS_PER_TOKEN * 0.3).toInt()
        val keepTailChars = (MAX_PROMPT_TOKENS * CHARS_PER_TOKEN * 0.7).toInt()

        val head = prompt.take(keepHeadChars)
        val tail = prompt.takeLast(keepTailChars)

        return "$head\n\n... (промпт обрезан из-за ограничения токенов) ...\n\n$tail"
    }
}
