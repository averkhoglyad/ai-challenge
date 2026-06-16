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
    val tokenUsageHistory: List<TokenUsage> = emptyList(),
    val accumulatedSummary: String? = null,
    val messageTags: Map<Int, Set<MessageTag>> = emptyMap()
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
     * Обновляет накопительное summary диалога.
     *
     * Используется механизмом сжатия контекста для хранения суммаризации
     * предыдущих сообщений. Возвращает новый экземпляр [Dialog] с обновлённым summary.
     *
     * @param newSummary новый текст суммаризации
     * @return новый экземпляр [Dialog] с обновлённым [accumulatedSummary]
     */
    fun updateAccumulatedSummary(newSummary: String): Dialog {
        return copy(accumulatedSummary = newSummary)
    }

    /**
     * Количество сжатых сообщений в диалоге.
     *
     * Вычисляется из [messageTags] — подсчитывает количество сообщений,
     * помеченных тегом [MessageTag.Compressed]. Это универсальный подход,
     * не зависящий от конкретного алгоритма сжатия.
     */
    val compressedMessageCount: Int
        get() = messageTags.values.count { MessageTag.Compressed in it }

    /**
     * Помечает сообщения с указанными индексами тегом.
     *
     * Индексы сообщений соответствуют позиции в списке [messages] (0-based).
     * Теги добавляются к уже существующим (не заменяются).
     * Используется стратегиями управления контекстом и механизмами сжатия
     * для визуализации истории диалога.
     *
     * @param tag тег для добавления
     * @param messageIndices индексы сообщений в списке [messages]
     * @return новый экземпляр [Dialog] с обновлёнными тегами
     */
    fun tagMessages(tag: MessageTag, vararg messageIndices: Int): Dialog {
        if (messageIndices.isEmpty()) return this
        val newTags = messageTags.toMutableMap()
        for (index in messageIndices) {
            val existing = newTags.getOrDefault(index, emptySet())
            newTags[index] = existing + tag
        }
        return copy(messageTags = newTags)
    }

    /**
     * Добавляет сообщение в диалог, если его ещё нет в истории.
     *
     * Проверяет наличие сообщения по содержимому, роли и времени создания.
     * Если сообщение уже есть — возвращает текущий экземпляр без изменений.
     *
     * @param message сообщение для добавления
     * @return новый экземпляр [Dialog] с добавленным сообщением или текущий, если сообщение уже есть
     */
    fun withMessage(message: ChatMessage): Dialog {
        val alreadyExists = messages.any {
            it.role == message.role &&
                    it.content == message.content &&
                    it.createdAt == message.createdAt
        }
        return if (alreadyExists) this else copy(messages = messages + message)
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
