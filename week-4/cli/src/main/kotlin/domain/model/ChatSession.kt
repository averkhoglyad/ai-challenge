package io.averkhogliad.ai.challenge.week4.cli.domain.model

import java.time.Instant

/**
 * Агрегат чат-сессии — корень консистентности для всего состояния чата.
 *
 * ## Архитектурная роль
 * - **Aggregate Root** — инкапсулирует метаданные, историю сообщений, память задачи и конфигурацию
 * - **Immutable** — все методы возвращают новый экземпляр
 *
 * ## Инварианты
 * - Только один чат может быть активным в каждый момент времени
 * - Архивированный чат не может быть активным
 * - [addMessage] обновляет `updatedAt` в метаданных
 *
 * ## Свойства
 * - [metadata] — метаданные чата (id, имя, временные метки, флаги)
 * - [messages] — линейная история сообщений
 * - [taskState] — текущее состояние памяти задачи
 * - [config] — конфигурация чата
 */
data class ChatSession(
    val metadata: ChatMetadata,
    val messages: List<ChatMessage>,
    val taskState: TaskState,
    val config: ChatConfig
) {

    /**
     * Добавляет сообщение в историю чата.
     *
     * @param message сообщение для добавления
     * @return новая копия сессии с добавленным сообщением и обновлённым [ChatMetadata.updatedAt]
     */
    fun addMessage(message: ChatMessage): ChatSession {
        require(message.sessionId == metadata.id) {
            "Message sessionId (${message.sessionId}) must match session id (${metadata.id})"
        }
        return copy(
            messages = messages + message,
            metadata = metadata.copy(updatedAt = Instant.now())
        )
    }

    /**
     * Обновляет состояние памяти задачи.
     *
     * @param newState новое состояние задачи
     * @return новая копия сессии с обновлённым [taskState] и [ChatMetadata.updatedAt]
     */
    fun updateTaskState(newState: TaskState): ChatSession = copy(
        taskState = newState,
        metadata = metadata.copy(updatedAt = Instant.now())
    )

    /**
     * Архивирует чат. Архивированный чат не может быть активным.
     *
     * @return новая копия сессии с флагами `archived = true` и `active = false`
     */
    fun archive(): ChatSession = copy(
        metadata = metadata.copy(
            archived = true,
            active = false,
            updatedAt = Instant.now()
        )
    )

    /**
     * Активирует чат.
     *
     * @return новая копия сессии с флагом `active = true`
     */
    fun activate(): ChatSession = copy(
        metadata = metadata.copy(
            active = true,
            updatedAt = Instant.now()
        )
    )

    /**
     * Переименовывает чат.
     *
     * @param newName новое имя чата
     * @param generated флаг, сгенерировано ли имя автоматически (LLM)
     * @return новая копия сессии с обновлённым именем
     */
    fun rename(newName: String, generated: Boolean = false): ChatSession {
        require(newName.isNotBlank()) { "Chat name cannot be blank" }
        require(newName.length <= config.nameMaxLength) {
            "Chat name exceeds max length: ${newName.length} > ${config.nameMaxLength}"
        }
        return copy(
            metadata = metadata.copy(
                name = newName,
                nameGenerated = generated,
                updatedAt = Instant.now()
            )
        )
    }

    /**
     * Возвращает последние N сообщений в рамках окна истории.
     *
     * @param limit максимальное количество возвращаемых сообщений
     * @return список последних сообщений
     */
    fun getRecentMessages(limit: Int = config.historyWindowSize): List<ChatMessage> =
        messages.takeLast(limit)

    /**
     * Проверяет, активна ли сессия.
     */
    fun isActive(): Boolean = metadata.active && !metadata.archived

    companion object {
        /**
         * Создаёт новую чат-сессию с параметрами по умолчанию.
         *
         * @param name имя чата
         * @param config конфигурация чата
         * @return новый экземпляр [ChatSession]
         */
        fun create(
            name: String = "New Chat",
            config: ChatConfig = ChatConfig()
        ): ChatSession = ChatSession(
            metadata = ChatMetadata.create(name),
            messages = emptyList(),
            taskState = TaskState.EMPTY,
            config = config
        )
    }
}
