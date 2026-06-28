package io.averkhogliad.ai.challenge.week3.cli.domain.service

import io.averkhogliad.ai.challenge.week3.cli.domain.model.*
import java.time.Instant

/**
 * Полный контекст памяти для формирования промпта LLM.
 *
 * @property workingMemory рабочая память (WM) с задачами, шагами и свёрткой
 * @property relevantFacts релевантные факты из LTM
 * @property recentMessages последние сообщения из STM (скользящее окно)
 */
data class MemoryContext(
    val workingMemory: WorkingMemory?,
    val relevantFacts: List<Fact>,
    val recentMessages: List<Message>
)

/**
 * Сервис управления памятью диалога (STM, WM, LTM).
 *
 * ## Архитектурная роль
 * - **Domain Service** — оркестрирует операции с STM, WM и LTM
 * - **Stateless** — состояние хранится в репозиториях
 *
 * ## Ответственность
 * - Управление сессиями диалога для разных уровней (TASK_LIST, TASK_DETAIL)
 * - Ограничение STM скользящим окном
 * - Формирование WM на основе текущего уровня
 * - Извлечение и поиск релевантных фактов из LTM
 * - Формирование полного контекста памяти для LLM
 */
class MemoryService(
    private val sessionRepository: DialogSessionRepository,
    private val taskRepository: TaskRepository? = null,
    private val taskStepRepository: TaskStepRepository? = null,
    private val factRepository: FactRepository? = null,
    private val stmWindowSize: Int = DEFAULT_STM_WINDOW_SIZE
) {
    /**
     * Получить или создать сессию для указанного уровня.
     *
     * @param level Уровень сессии (TASK_LIST или TASK_DETAIL)
     * @param taskId ID задачи (обязателен для TASK_DETAIL)
     * @return Сессия диалога
     */
    suspend fun getSessionForLevel(level: SessionLevel, taskId: TaskId? = null): DialogSession {
        val sessionId = createSessionId(level, taskId)
        return sessionRepository.findById(sessionId)
            ?: createNewSession(sessionId, level, taskId)
    }

    /**
     * Добавить сообщение в сессию для указанного уровня.
     *
     * @param level Уровень сессии
     * @param taskId ID задачи (для TASK_DETAIL)
     * @param message Сообщение для добавления
     * @return Обновлённая сессия
     */
    suspend fun addMessageToSession(
        level: SessionLevel,
        taskId: TaskId?,
        message: Message
    ): DialogSession {
        val session = getSessionForLevel(level, taskId)
        val updatedSession = session.addMessage(message)
        return sessionRepository.save(updatedSession)
    }

    /**
     * Очистить STM для указанного уровня.
     *
     * @param level Уровень сессии
     * @param taskId ID задачи (для TASK_DETAIL)
     * @return Очищенная сессия
     */
    suspend fun clearSession(level: SessionLevel, taskId: TaskId? = null): DialogSession {
        val session = getSessionForLevel(level, taskId)
        val clearedSession = session.clearMessages()
        sessionRepository.save(clearedSession)
        return clearedSession
    }

    /**
     * Получить последние сообщения из STM с ограничением скользящего окна.
     *
     * @param level Уровень сессии
     * @param taskId ID задачи (для TASK_DETAIL)
     * @param limit Максимальное количество сообщений (по умолчанию = stmWindowSize)
     * @return Список последних сообщений
     */
    suspend fun getRecentMessages(
        level: SessionLevel,
        taskId: TaskId? = null,
        limit: Int = stmWindowSize
    ): List<Message> {
        val session = getSessionForLevel(level, taskId)
        return session.getRecentMessages(limit)
    }

    /**
     * Получить статус памяти для указанного уровня.
     *
     * @param level Уровень сессии
     * @param taskId ID задачи (для TASK_DETAIL)
     * @return Статус памяти
     */
    suspend fun getMemoryStatus(level: SessionLevel, taskId: TaskId? = null): MemoryStatus {
        val session = getSessionForLevel(level, taskId)
        val ltmCount = factRepository?.count() ?: 0
        return MemoryStatus(
            sessionId = session.id,
            level = session.level,
            taskId = taskId,
            messageCount = session.messages.size,
            createdAt = session.createdAt,
            updatedAt = session.updatedAt,
            ltmFactCount = ltmCount
        )
    }

    /**
     * Сохранить сообщение пользователя в STM.
     *
     * @param level Уровень сессии
     * @param taskId ID задачи (для TASK_DETAIL)
     * @param content текст сообщения пользователя
     * @return сохранённое сообщение
     */
    suspend fun saveUserMessage(
        level: SessionLevel,
        taskId: TaskId? = null,
        content: String
    ): Message {
        val session = getSessionForLevel(level, taskId)
        val message = Message.create(
            sessionId = session.id,
            role = MessageRole.USER,
            content = content
        )
        addMessageToSession(level, taskId, message)
        return message
    }

    /**
     * Сохранить ответ ассистента в STM.
     *
     * @param level Уровень сессии
     * @param taskId ID задачи (для TASK_DETAIL)
     * @param content текст ответа ассистента
     * @return сохранённое сообщение
     */
    suspend fun saveAssistantMessage(
        level: SessionLevel,
        taskId: TaskId? = null,
        content: String
    ): Message {
        val session = getSessionForLevel(level, taskId)
        val message = Message.create(
            sessionId = session.id,
            role = MessageRole.ASSISTANT,
            content = content
        )
        addMessageToSession(level, taskId, message)
        return message
    }

    /**
     * Получить полный контекст памяти для формирования промпта LLM.
     *
     * Собирает WM (задачи + шаги), релевантные факты из LTM и историю диалога из STM.
     *
     * @param level Уровень сессии
     * @param taskId ID задачи (для TASK_DETAIL)
     * @param userQuery запрос пользователя для поиска релевантных фактов
     * @param factSearchLimit максимальное количество релевантных фактов (по умолчанию 5)
     * @return [MemoryContext] с WM, фактами и сообщениями
     */
    suspend fun getFullMemoryContext(
        level: SessionLevel,
        taskId: TaskId? = null,
        userQuery: String? = null,
        factSearchLimit: Int = 5
    ): MemoryContext {
        val relevantFacts = if (userQuery != null && factRepository != null) {
            factRepository.search(userQuery).take(factSearchLimit)
        } else {
            emptyList()
        }

        val recentMessages = getRecentMessages(level, taskId, stmWindowSize)

        val workingMemory = buildWorkingMemory(level, taskId)

        return MemoryContext(
            workingMemory = workingMemory,
            relevantFacts = relevantFacts,
            recentMessages = recentMessages
        )
    }

    /**
     * Построить WorkingMemory на основе уровня сессии.
     */
    private suspend fun buildWorkingMemory(
        level: SessionLevel,
        taskId: TaskId?
    ): WorkingMemory? {
        val sessionId = createSessionId(level, taskId)
        return when (level) {
            SessionLevel.TASK_LIST -> {
                val tasks = taskRepository?.findAll() ?: emptyList()
                if (tasks.isEmpty()) null else {
                    WorkingMemory(
                        sessionId = sessionId,
                        currentMessages = emptyList(),
                        summary = null,
                        steps = emptyList()
                    )
                }
            }

            SessionLevel.TASK_DETAIL -> {
                val steps = taskId?.let { taskStepRepository?.findByTaskId(it) } ?: emptyList()
                val taskDescription = taskId?.let { taskRepository?.findById(it)?.description }
                WorkingMemory(
                    sessionId = sessionId,
                    currentMessages = emptyList(),
                    summary = null,
                    steps = steps,
                    taskDescription = taskDescription
                )
            }
        }
    }

    /**
     * Переключиться на уровень TASK_DETAIL для указанной задачи.
     *
     * @param taskId ID задачи
     * @return Сессия для уровня TASK_DETAIL
     */
    suspend fun switchToTaskLevel(taskId: TaskId): DialogSession {
        return getSessionForLevel(SessionLevel.TASK_DETAIL, taskId)
    }

    /**
     * Переключиться на уровень TASK_LIST.
     *
     * @return Сессия для уровня TASK_LIST
     */
    suspend fun switchToTaskListLevel(): DialogSession {
        return getSessionForLevel(SessionLevel.TASK_LIST)
    }

    /**
     * Создать ID сессии на основе уровня и ID задачи.
     */
    private fun createSessionId(level: SessionLevel, taskId: TaskId?): SessionId {
        val idValue = when (level) {
            SessionLevel.TASK_LIST -> "session_task_list"
            SessionLevel.TASK_DETAIL -> "session_task_${taskId?.value ?: "unknown"}"
        }
        return SessionId(idValue)
    }

    /**
     * Создать новую сессию.
     */
    private suspend fun createNewSession(
        sessionId: SessionId,
        level: SessionLevel,
        taskId: TaskId?
    ): DialogSession {
        val now = Instant.now()
        val session = DialogSession(
            id = sessionId,
            level = level,
            taskId = taskId,
            messages = emptyList(),
            createdAt = now,
            updatedAt = now
        )
        sessionRepository.save(session)
        return session
    }

    companion object {
        /** Размер скользящего окна STM по умолчанию */
        const val DEFAULT_STM_WINDOW_SIZE = 20
    }
}
