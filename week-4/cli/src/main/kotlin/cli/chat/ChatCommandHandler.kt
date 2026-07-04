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
        return try {
            val id = UUID.fromString(command.id)
            val session = chatSessionManager.switchToSession(id)
            notificationRenderer.renderSwitchedTo(session.metadata.name, id)
            state.copy(activeChatSessionId = id.toString())
        } catch (e: IllegalArgumentException) {
            notificationRenderer.renderError("Неверный формат ID: ${command.id}")
            state
        } catch (e: IllegalStateException) {
            notificationRenderer.renderError(e.message ?: "Сессия не найдена: ${command.id}")
            state
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
        return try {
            val id = UUID.fromString(command.id)
            chatSessionManager.deleteSession(id)
            notificationRenderer.renderChatDeleted(id)

            // Если удалили активный чат — сбрасываем activeChatSessionId
            if (state.activeChatSessionId == command.id) {
                // После удаления ChatSessionManager активирует последний или создаст новый
                val newActive = chatSessionManager.getActiveSession()
                state.copy(activeChatSessionId = newActive?.metadata?.id?.toString())
            } else {
                state
            }
        } catch (e: IllegalArgumentException) {
            notificationRenderer.renderError("Неверный формат ID: ${command.id}")
            state
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
}
