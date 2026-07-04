package io.averkhogliad.ai.challenge.week4.cli.infrastructure.chat

import io.averkhogliad.ai.challenge.week4.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.service.ChatNameGenerator
import io.averkhogliad.ai.challenge.week4.cli.domain.service.LlmPort
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.chat.LlmChatNameGenerator.Companion.MAX_NAME_LENGTH
import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatMessage as DomainChatMessage
import io.averkhogliad.ai.challenge.week4.cli.domain.service.ChatMessage as LlmChatMessage

/**
 * LLM-реализация [ChatNameGenerator] — генерирует имя чата
 * на основе первых сообщений диалога.
 *
 * ## Архитектурная роль
 * - **Infrastructure Layer** — реализует domain-порт [ChatNameGenerator]
 * - Использует [LlmPort] для вызова LLM
 *
 * ## Логика
 * 1. Формирует промпт с инструкцией сгенерировать имя чата (3-7 слов, ≤50 символов)
 * 2. Передаёт первые сообщения диалога как контекст
 * 3. Парсит ответ LLM как строку-имя
 * 4. Валидирует длину (≤50), обрезает если нужно
 * 5. При ошибке — возвращает [Result.failure]
 *
 * @param llmPort порт LLM для отправки запросов
 */
class LlmChatNameGenerator(
    private val llmPort: LlmPort
) : ChatNameGenerator {

    companion object {
        private const val MAX_NAME_LENGTH = 50
    }

    override suspend fun generate(messages: List<DomainChatMessage>): Result<String> {
        return try {
            val systemPrompt = buildString {
                appendLine("You are a chat naming assistant. Generate a short, informative name for the chat based on the first message exchange.")
                appendLine("Requirements:")
                appendLine("- 3 to 7 words")
                appendLine("- Maximum $MAX_NAME_LENGTH characters")
                appendLine("- Capture the essence of the conversation topic")
                appendLine("- Output ONLY the name, no quotes, no extra text, no markdown")
            }

            val userContent = buildUserContent(messages)

            val llmMessages = listOf(
                LlmChatMessage.system(systemPrompt),
                LlmChatMessage.user(userContent)
            )

            val config = TaskExecutionConfig(temperature = 0.3, maxTokens = 64)

            val result = llmPort.chatWithMessages(llmMessages, config)
            when (result) {
                is io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult.Success -> {
                    val name = sanitizeName(result.content)
                    Result.success(name)
                }

                is io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult.Error ->
                    Result.failure(Exception("LLM error: ${result.message}"))

                is io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult.Partial ->
                    Result.failure(Exception("LLM returned partial result"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Формирует контент пользовательского сообщения из domain-сообщений.
     */
    private fun buildUserContent(messages: List<DomainChatMessage>): String = buildString {
        appendLine("Generate a chat name for this conversation:")
        messages.forEach { msg ->
            val role = when (msg) {
                is DomainChatMessage.User -> "User"
                is DomainChatMessage.Assistant -> "Assistant"
                is DomainChatMessage.System -> "System"
            }
            val text = when (msg) {
                is DomainChatMessage.User -> msg.text
                is DomainChatMessage.Assistant -> msg.text
                is DomainChatMessage.System -> msg.text
            }
            appendLine("$role: $text")
        }
    }

    /**
     * Очищает и валидирует сгенерированное имя:
     * - Убирает лишние кавычки и пробелы
     * - Обрезает до [MAX_NAME_LENGTH] символов
     * - Если имя пустое — возвращает fallback
     */
    private fun sanitizeName(raw: String): String {
        val cleaned = raw
            .trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .trim()

        return when {
            cleaned.length > MAX_NAME_LENGTH -> cleaned.take(MAX_NAME_LENGTH)
            cleaned.isBlank() -> "New Chat"
            else -> cleaned
        }
    }
}
