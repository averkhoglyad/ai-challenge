package io.averkhogliad.ai.challenge.week2.domain.model

import java.time.Instant

/**
 * Доменная модель сессии диалога в системе памяти.
 *
 * ## Архитектурная роль
 * - **Aggregate Root** — корень агрегата для управления диалоговой сессией
 * - **Immutable** — все изменения возвращают новый экземпляр
 *
 * ## Свойства
 * - [id] — уникальный идентификатор сессии
 * - [level] — уровень сессии (TASK_LIST или TASK_DETAIL)
 * - [taskId] — опциональный идентификатор задачи (для TASK_DETAIL)
 * - [messages] — список сообщений в сессии
 * - [createdAt] — время создания сессии
 * - [updatedAt] — время последнего обновления сессии
 *
 * ## Бизнес-логика
 * - [addMessage()] — добавляет сообщение в сессию
 * - [isActive()] — проверяет, активна ли сессия
 */
data class DialogSession(
    val id: SessionId,
    val level: SessionLevel,
    val taskId: TaskId?,
    val messages: List<Message>,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        if (level == SessionLevel.TASK_DETAIL) {
            requireNotNull(taskId) { "taskId is required for TASK_DETAIL level" }
        }
    }

    /**
     * Проверяет, активна ли сессия (содержит ли она сообщения).
     */
    fun isActive(): Boolean = messages.isNotEmpty()

    /**
     * Добавляет сообщение в сессию.
     *
     * @param message сообщение для добавления
     * @return новая копия сессии с добавленным сообщением и обновлённым updatedAt
     */
    fun addMessage(message: Message): DialogSession {
        require(message.sessionId == id) {
            "Message sessionId must match session id"
        }
        return copy(
            messages = messages + message,
            updatedAt = Instant.now()
        )
    }

    /**
     * Очищает все сообщения в сессии.
     *
     * @return новая копия сессии с пустым списком сообщений и обновлённым updatedAt
     */
    fun clearMessages(): DialogSession {
        return copy(
            messages = emptyList(),
            updatedAt = Instant.now()
        )
    }

    /**
     * Получает последние N сообщений из сессии.
     *
     * @param limit максимальное количество сообщений
     * @return список последних сообщений
     */
    fun getRecentMessages(limit: Int): List<Message> {
        return messages.takeLast(limit)
    }

    /**
     * Создаёт новую сессию с указанными параметрами.
     *
     * @param level уровень сессии
     * @param taskId опциональный идентификатор задачи
     * @return новый экземпляр [DialogSession]
     */
    companion object {
        fun create(
            level: SessionLevel,
            taskId: TaskId? = null
        ): DialogSession {
            val now = Instant.now()
            return DialogSession(
                id = SessionId.generate(),
                level = level,
                taskId = taskId,
                messages = emptyList(),
                createdAt = now,
                updatedAt = now
            )
        }
    }
}
