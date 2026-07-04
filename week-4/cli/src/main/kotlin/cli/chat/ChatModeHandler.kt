package io.averkhogliad.ai.challenge.week4.cli.cli.chat

import io.averkhogliad.ai.challenge.week4.cli.application.chat.ChatExecutor
import io.averkhogliad.ai.challenge.week4.cli.application.chat.ChatSessionManager
import io.averkhogliad.ai.challenge.week4.cli.cli.CliState
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskStateDelta
import java.util.*

/**
 * Обработчик ввода в режиме чата.
 *
 * Оркестрирует pipeline:
 * 1. Если ввод — команда (начинается с `:`) → парсинг и обработка через
 *    [ChatCommandHandler] / [TaskStateCommandHandler]
 * 2. Иначе → отправка сообщения в [ChatExecutor]
 * 3. Рендеринг ответа через [ChatAnswerRenderer]
 * 4. Уведомления об изменении памяти через [ChatNotificationRenderer]
 */
class ChatModeHandler(
    private val chatExecutor: ChatExecutor,
    private val chatSessionManager: ChatSessionManager,
    private val chatCommandHandler: ChatCommandHandler,
    private val taskStateCommandHandler: TaskStateCommandHandler,
    private val answerRenderer: ChatAnswerRenderer,
    private val notificationRenderer: ChatNotificationRenderer
) {

    /** Кэш имени активной сессии: пара (sessionId, name). */
    private var cachedSessionName: Pair<UUID, String>? = null

    /**
     * Обрабатывает сообщение пользователя в режиме чата.
     *
     * @param input текст сообщения (не команда — без префикса `:`)
     * @param state текущее состояние CLI
     * @return обновлённое состояние
     */
    suspend fun handleMessage(input: String, state: CliState): CliState {
        // Получить или создать активную сессию
        val sessionId = resolveSessionId(state)
            ?: return enterChatMode(state)

        val config = state.executionConfig
        val ragState = state.ragState

        // Выполнить pipeline через ChatExecutor
        val result = try {
            chatExecutor.execute(
                userInput = input,
                sessionId = sessionId,
                executionConfig = config,
                ragState = ragState
            )
        } catch (e: Exception) {
            answerRenderer.renderError("Ошибка выполнения: ${e.message}")
            return state
        }

        // Рендерить ответ
        if (result.citations.isNotEmpty()) {
            answerRenderer.renderAnswer(result.answer, result.citations)
        } else {
            answerRenderer.renderAnswer(result.answer, emptyList())
        }

        // Уведомление об изменении памяти
        if (result.taskStateUpdated && result.stateDelta != null) {
            val delta = result.stateDelta
            val details = buildDeltaDetails(delta)
            notificationRenderer.renderTaskStateUpdated(details)
        }

        return state
    }

    /**
     * Обрабатывает команду (начинается с `:`) в режиме чата.
     *
     * @param commandName имя команды без `:`
     * @param args аргументы команды
     * @param state текущее состояние
     * @return обновлённое состояние
     */
    suspend fun handleCommand(commandName: String, args: String, state: CliState): CliState {
        // Пробуем chat-команды
        val chatCommand = ChatCommandParser.parseChatCommand(commandName, args)
        if (chatCommand != null) {
            return chatCommandHandler.handle(chatCommand, state)
        }

        // Пробуем task-state команды
        val taskCommand = ChatCommandParser.parseTaskStateCommand(commandName, args)
        if (taskCommand != null) {
            return taskStateCommandHandler.handle(taskCommand, state)
        }

        // Неизвестная команда
        notificationRenderer.renderError("Неизвестная команда: :$commandName")
        return state
    }

    /**
     * Вход в режим чата: создаёт или активирует сессию.
     *
     * @return обновлённое состояние с chatMode=true
     */
    suspend fun enterChatMode(state: CliState): CliState {
        val session = chatSessionManager.getActiveSession()
            ?: chatSessionManager.createSession()

        notificationRenderer.renderChatModeEntered(session.metadata.name)

        return state.copy(
            chatMode = true,
            activeChatSessionId = session.metadata.id.toString()
        )
    }

    /**
     * Выход из режима чата.
     */
    fun exitChatMode(state: CliState): CliState {
        notificationRenderer.renderChatModeExited()
        return state.copy(chatMode = false)
    }

    /**
     * Возвращает prompt для режима чата.
     */
    suspend fun getPrompt(state: CliState): String {
        val sessionId = state.activeChatSessionId
        if (sessionId == null) return "[Chat] > "

        return try {
            val id = UUID.fromString(sessionId)
            val name = if (cachedSessionName?.first == id) {
                cachedSessionName!!.second
            } else {
                val session = chatSessionManager.getActiveSession()
                val n = session?.metadata?.name ?: "Chat"
                cachedSessionName = id to n
                n
            }
            if (name.length > 20) "[${name.take(17)}...] > " else "[$name] > "
        } catch (e: Exception) {
            "[Chat] > "
        }
    }

    /**
     * Очищает историю сообщений в чат-сессии.
     */
    suspend fun clearHistory(sessionId: UUID) {
        chatSessionManager.clearHistory(sessionId)
    }

    // ──── Private helpers ────

    private fun resolveSessionId(state: CliState): UUID? {
        val idStr = state.activeChatSessionId ?: return null
        return try {
            UUID.fromString(idStr)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun buildDeltaDetails(delta: TaskStateDelta): String {
        return when (delta) {
            is TaskStateDelta.SetGoal -> "Цель установлена"
            is TaskStateDelta.AddTerm -> "Термин '${delta.name}' добавлен"
            is TaskStateDelta.RemoveTerm -> "Термин '${delta.name}' удалён"
            is TaskStateDelta.AddConstraint -> "Ограничение добавлено"
            is TaskStateDelta.RemoveConstraint -> "Ограничение #${delta.index} удалено"
            is TaskStateDelta.AddClarifiedFact -> "Уточнённый факт добавлен"
            is TaskStateDelta.RemoveClarifiedFact -> "Уточнённый факт #${delta.index} удалён"
            is TaskStateDelta.ResetAll -> "Память сброшена"
            is TaskStateDelta.NoChanges -> "Без изменений"
            is TaskStateDelta.Composite -> {
                val parts = delta.deltas.map { buildDeltaDetails(it) }
                parts.joinToString(", ")
            }
        }
    }
}
