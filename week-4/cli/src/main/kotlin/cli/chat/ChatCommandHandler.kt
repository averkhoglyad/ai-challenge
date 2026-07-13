package io.averkhogliad.ai.challenge.week4.cli.cli.chat

import io.averkhogliad.ai.challenge.week4.cli.application.chat.ChatSessionManager
import io.averkhogliad.ai.challenge.week4.cli.cli.CliState
import io.averkhogliad.ai.challenge.week4.cli.domain.service.ChatSessionRepository
import java.util.*

/**
 * Обработчик команд управления чатами.
 *
 * Применяет [ChatCommand] через [ChatSessionManager] и возвращает обновлённое [CliState].
 * Не содержит бизнес-логики — только вызовы application-сервисов и обновление состояния.
 */
class ChatCommandHandler(
    private val chatSessionManager: ChatSessionManager,
    private val repository: ChatSessionRepository,
    private val listRenderer: ChatListRenderer,
    private val notificationRenderer: ChatNotificationRenderer,
    private val historyRenderer: ChatHistoryRenderer
) {

    /**
     * Диспетчеризует [ChatCommand] и возвращает обновлённое состояние.
     */
    suspend fun handle(command: ChatCommand, state: CliState): CliState = when (command) {
        is ChatCommand.New -> handleNew(state)
        is ChatCommand.List -> handleList(state)
        is ChatCommand.Switch -> handleSwitch(command, state)
        is ChatCommand.Rename -> handleRename(command, state)
        is ChatCommand.Delete -> handleDelete(command, state)
        is ChatCommand.Archive -> handleArchive(state)
        is ChatCommand.History -> handleHistory(command, state)
    }

    private suspend fun handleNew(state: CliState): CliState {
        val session = chatSessionManager.createSession()
        notificationRenderer.renderChatCreated(session.metadata.name, session.metadata.id)
        return state.copy(activeChatSessionId = session.metadata.id.toString())
    }

    private suspend fun handleList(state: CliState): CliState {
        val sessions = chatSessionManager.listSessions()
        val activeId = state.activeChatSessionId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        listRenderer.render(sessions, activeId)
        return state
    }

    private suspend fun handleSwitch(command: ChatCommand.Switch, state: CliState): CliState {
        return when (val result = resolveSessionId(command.id)) {
            is ResolveResult.Found -> {
                try {
                    val session = chatSessionManager.switchToSession(result.id)
                    notificationRenderer.renderSwitchedTo(session.metadata.name, result.id)
                    state.copy(activeChatSessionId = result.id.toString())
                } catch (e: IllegalStateException) {
                    notificationRenderer.renderError(e.message ?: "Сессия не найдена: ${command.id}")
                    state
                }
            }

            is ResolveResult.NotFound -> {
                notificationRenderer.renderError("Сессия не найдена: ${command.id}")
                state
            }

            is ResolveResult.BadFormat -> {
                notificationRenderer.renderError("Неверный формат ID: ${command.id}")
                state
            }
        }
    }

    private suspend fun handleRename(command: ChatCommand.Rename, state: CliState): CliState {
        val sessionId = state.activeChatSessionId
            ?: run {
                notificationRenderer.renderError("Нет активного чата для переименования")
                return state
            }
        return try {
            val id = UUID.fromString(sessionId)
            val session = chatSessionManager.renameSession(id, command.name)
            notificationRenderer.renderChatRenamed(session.metadata.name)
            state
        } catch (e: IllegalArgumentException) {
            notificationRenderer.renderError("Неверный формат ID: $sessionId")
            state
        } catch (e: IllegalStateException) {
            notificationRenderer.renderError(e.message ?: "Ошибка переименования")
            state
        }
    }

    private suspend fun handleDelete(command: ChatCommand.Delete, state: CliState): CliState {
        return when (val result = resolveSessionId(command.id)) {
            is ResolveResult.Found -> {
                val deleted = chatSessionManager.deleteSession(result.id)
                if (!deleted) {
                    notificationRenderer.renderError("Сессия не найдена: ${command.id}")
                    return state
                }
                notificationRenderer.renderChatDeleted(result.id)

                if (state.activeChatSessionId == result.id.toString()) {
                    val newActive = chatSessionManager.getActiveSession()
                    state.copy(activeChatSessionId = newActive?.metadata?.id?.toString())
                } else {
                    state
                }
            }

            is ResolveResult.NotFound -> {
                notificationRenderer.renderError("Сессия не найдена: ${command.id}")
                state
            }

            is ResolveResult.BadFormat -> {
                notificationRenderer.renderError("Неверный формат ID: ${command.id}")
                state
            }
        }
    }

    private suspend fun handleArchive(state: CliState): CliState {
        val sessionId = state.activeChatSessionId
            ?: run {
                notificationRenderer.renderError("Нет активного чата для архивации")
                return state
            }
        return try {
            val id = UUID.fromString(sessionId)
            chatSessionManager.archiveSession(id)
            notificationRenderer.renderChatArchived(id)

            // После архивации находим новый активный чат
            val newActive = chatSessionManager.getActiveSession()
            state.copy(activeChatSessionId = newActive?.metadata?.id?.toString())
        } catch (e: IllegalArgumentException) {
            notificationRenderer.renderError("Неверный формат ID: $sessionId")
            state
        }
    }

    private suspend fun handleHistory(command: ChatCommand.History, state: CliState): CliState {
        val sessionId = state.activeChatSessionId
            ?: run {
                notificationRenderer.renderError("Нет активного чата для просмотра истории")
                return state
            }
        return try {
            val id = UUID.fromString(sessionId)
            val sessionResult = repository.loadById(id)
                .getOrNull()
                ?: throw IllegalStateException("Сессия не найдена: $sessionId")
            historyRenderer.render(sessionResult.messages, command.limit)
            state
        } catch (e: Exception) {
            notificationRenderer.renderError("Ошибка при отображении истории: ${e.message}")
            state
        }
    }

    /**
     * Результат разрешения идентификатора сессии.
     */
    private sealed class ResolveResult {
        data class Found(val id: UUID) : ResolveResult()
        data object NotFound : ResolveResult()
        data object BadFormat : ResolveResult()
    }

    /**
     * Разрешает идентификатор сессии: числовой индекс (1-based), UUID или префикс UUID.
     *
     * @param raw строка — номер из списка, полный UUID или первые символы UUID
     * @return [ResolveResult] с найденным UUID, признаком отсутствия или неверного формата
     */
    private suspend fun resolveSessionId(raw: String): ResolveResult {
        val sessions = chatSessionManager.listSessions()
        // 1. Числовой индекс (1-based)
        val index = raw.toIntOrNull()
        if (index != null) {
            if (index >= 1) {
                val session = sessions.getOrNull(index - 1)
                if (session != null) return ResolveResult.Found(session.metadata.id)
            }
            return ResolveResult.NotFound
        }
        // 2. Полный UUID
        runCatching { UUID.fromString(raw) }.getOrNull()?.let { return ResolveResult.Found(it) }
        // 3. Поиск по префиксу UUID (минимум 4 символа)
        if (raw.length >= 4) {
            val prefix = raw.lowercase()
            val match = sessions
                .map { it.metadata.id }
                .firstOrNull { it.toString().lowercase().startsWith(prefix) }
            if (match != null) return ResolveResult.Found(match)
            return ResolveResult.NotFound
        }
        return ResolveResult.BadFormat
    }
}
