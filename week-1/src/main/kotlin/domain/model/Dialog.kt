package io.averkhogliad.ai.challenge.week1.domain.model

import io.averkhogliad.ai.challenge.week1.domain.service.ChatMessage
import io.averkhogliad.ai.challenge.week1.domain.telemetry.TokenUsage
import java.time.Instant

/**
 * Immutable domain-модель диалога.
 *
 * Содержит полную историю сообщений, метаданные диалога и историю использования токенов.
 * Все операции возвращают новый экземпляр (immutable), что гарантирует
 * безопасность при параллельной работе и упрощает тестирование.
 *
 * ## Архитектурные решения
 * - **Immutable** — все поля val, методы возвращают новые экземпляры
 * - **Functional Core** — чистая domain-логика без побочных эффектов
 * - **Инкапсуляция** — методы [addUserMessage], [addAssistantMessage],
 *   [addTokenUsage] гарантируют корректное обновление состояния
 *
 * @property id уникальный идентификатор диалога
 * @property title человекочитаемое название диалога
 * @property messages история сообщений (system, user, assistant)
 * @property createdAt время создания диалога
 * @property updatedAt время последнего обновления
 * @property tokenUsageHistory история использования токенов для каждого LLM-вызова
 */
data class Dialog(
    val id: DialogId,
    val title: String,
    val messages: List<ChatMessage>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val tokenUsageHistory: List<TokenUsage> = emptyList()
) {
    init {
        require(title.isNotBlank()) { "Dialog title cannot be blank" }
    }

    /**
     * Добавляет пользовательское сообщение в диалог.
     *
     * Возвращает новый экземпляр [Dialog] с обновлённой историей сообщений
     * и обновлённым [updatedAt].
     *
     * @param content текст пользовательского сообщения
     * @param timestamp время добавления сообщения (по умолчанию текущее время)
     * @return новый экземпляр [Dialog] с добавленным сообщением
     */
    fun addUserMessage(content: String, timestamp: Instant = Instant.now()): Dialog {
        val newMessage = ChatMessage.user(content)
        return copy(
            messages = messages + newMessage,
            updatedAt = timestamp
        )
    }

    /**
     * Добавляет сообщение ассистента в диалог.
     *
     * Возвращает новый экземпляр [Dialog] с обновлённой историей сообщений
     * и обновлённым [updatedAt].
     *
     * @param content текст сообщения ассистента
     * @param timestamp время добавления сообщения (по умолчанию текущее время)
     * @return новый экземпляр [Dialog] с добавленным сообщением
     */
    fun addAssistantMessage(content: String, timestamp: Instant = Instant.now()): Dialog {
        val newMessage = ChatMessage.assistant(content)
        return copy(
            messages = messages + newMessage,
            updatedAt = timestamp
        )
    }

    /**
     * Добавляет запись об использовании токенов в историю диалога.
     *
     * @param usage использование токенов для одного LLM-вызова
     * @return новый экземпляр [Dialog] с обновлённой историей
     */
    fun addTokenUsage(usage: TokenUsage): Dialog {
        return copy(tokenUsageHistory = tokenUsageHistory + usage)
    }

    /**
     * Возвращает количество сообщений в диалоге.
     */
    val messageCount: Int get() = messages.size

    /**
     * Кумулятивное использование токенов за всю историю диалога.
     * Возвращает `null`, если история пуста.
     */
    val totalTokenUsage: TokenUsage?
        get() = if (tokenUsageHistory.isEmpty()) null
        else TokenUsage(
            promptTokens = tokenUsageHistory.sumOf { it.promptTokens },
            completionTokens = tokenUsageHistory.sumOf { it.completionTokens },
            totalTokens = tokenUsageHistory.sumOf { it.totalTokens }
        )

    companion object {
        /**
         * Создаёт новый диалог с пустой историей сообщений.
         *
         * @param id уникальный идентификатор
         * @param title название диалога
         * @return новый экземпляр [Dialog]
         */
        fun create(id: DialogId, title: String): Dialog {
            val now = Instant.now()
            return Dialog(
                id = id,
                title = title,
                messages = emptyList(),
                createdAt = now,
                updatedAt = now
            )
        }
    }
}
