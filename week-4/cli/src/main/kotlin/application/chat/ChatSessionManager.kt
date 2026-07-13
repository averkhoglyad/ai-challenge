package io.averkhogliad.ai.challenge.week4.cli.application.chat

import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatMessage
import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatSession
import io.averkhogliad.ai.challenge.week4.cli.domain.service.ChatNameGenerator
import io.averkhogliad.ai.challenge.week4.cli.domain.service.ChatSessionRepository
import java.util.*

/**
 * Управление множественными чат-сессиями.
 *
 * ## Бизнес-правила
 * - Только один активный чат одновременно
 * - При создании нового: старый архивируется
 * - При удалении активного: активируется последний неархивированный (или создаётся новый)
 * - Автоименование: после первого обмена (user + assistant), если имя не сгенерировано
 *   и [ChatConfig.autoNameEnabled] — вызывается [ChatNameGenerator]
 *
 * ## Архитектурная роль
 * - **Application Layer** — оркестрация, координация портов
 * - **Не зависит** от UI и infrastructure
 * - **Зависит только** от domain-портов и моделей
 *
 * @property repository порт персистентности чат-сессий
 * @property nameGenerator порт автоименования чатов (LLM)
 * @property config конфигурация чата
 */
class ChatSessionManager(
    private val repository: ChatSessionRepository,
    private val nameGenerator: ChatNameGenerator,
    private val config: ChatConfig
) {
    companion object {
        private const val TAG = "ChatSessionManager"
    }

    /**
     * Создаёт новую чат-сессию.
     *
     * Предыдущая активная сессия архивируется.
     *
     * @param name имя чата (по умолчанию "New Chat")
     * @return созданная сессия
     */
    suspend fun createSession(name: String = "New Chat"): ChatSession {
        // Архивация предыдущей активной сессии
        archiveActiveIfExists()

        val session = ChatSession.create(name = name, config = config)
        repository.save(session).onFailure { error ->
            System.err.println("[$TAG] Failed to save new session: ${error.message}")
        }

        return session
    }

    /**
     * Переключается на сессию с указанным идентификатором.
     *
     * Текущая активная деактивируется, целевая активируется.
     *
     * @param id идентификатор целевой сессии
     * @return активированная сессия
     * @throws IllegalStateException если сессия не найдена
     */
    suspend fun switchToSession(id: UUID): ChatSession {
        val result = repository.loadById(id)
        val session = result
            .onFailure { error ->
                System.err.println("[$TAG] Failed to load session $id: ${error.message}")
            }
            .getOrNull()
            ?: throw IllegalStateException("Chat session not found: $id")

        repository.setActive(id).onFailure { error ->
            System.err.println("[$TAG] Failed to set active session $id: ${error.message}")
        }

        return session.activate()
    }

    /**
     * Возвращает активную чат-сессию.
     *
     * @return активная сессия или `null`, если активной нет
     */
    suspend fun getActiveSession(): ChatSession? {
        return repository.loadActive()
            .onFailure { error ->
                System.err.println("[$TAG] Failed to load active session: ${error.message}")
            }
            .getOrNull()
    }

    /**
     * Возвращает список всех чат-сессий.
     *
     * @return список всех сессий (может быть пустым)
     */
    suspend fun listSessions(): List<ChatSession> {
        return repository.listSessions()
            .onFailure { error ->
                System.err.println("[$TAG] Failed to list sessions: ${error.message}")
            }
            .getOrDefault(emptyList())
    }

    /**
     * Удаляет чат-сессию.
     *
     * При удалении активной сессии: активируется последняя неархивированная,
     * либо создаётся новая.
     *
     * @param id идентификатор удаляемой сессии
     * @return true если сессия существовала и была удалена, false если не найдена
     */
    suspend fun deleteSession(id: UUID): Boolean {
        val loaded = repository.loadById(id).getOrNull()
        if (loaded == null) return false

        val wasActive = loaded.isActive()

        repository.deleteSession(id).onFailure { error ->
            System.err.println("[$TAG] Failed to delete session $id: ${error.message}")
        }

        if (wasActive) {
            activateLastOrCreate()
        }
        return true
    }

    /**
     * Переименовывает чат-сессию.
     *
     * @param id идентификатор сессии
     * @param name новое имя
     * @return обновлённая сессия
     * @throws IllegalStateException если сессия не найдена
     */
    suspend fun renameSession(id: UUID, name: String): ChatSession {
        val session = repository.loadById(id)
            .onFailure { error ->
                System.err.println("[$TAG] Failed to load session $id: ${error.message}")
            }
            .getOrNull()
            ?: throw IllegalStateException("Chat session not found: $id")

        val renamed = session.rename(name, generated = false)
        repository.save(renamed).onFailure { error ->
            System.err.println("[$TAG] Failed to save renamed session $id: ${error.message}")
        }

        return renamed
    }

    /**
     * Архивирует чат-сессию.
     *
     * Архивированный чат не может быть активным.
     *
     * @param id идентификатор сессии
     */
    suspend fun archiveSession(id: UUID) {
        repository.archiveSession(id).onFailure { error ->
            System.err.println("[$TAG] Failed to archive session $id: ${error.message}")
        }
    }

    /**
     * Автоматически генерирует имя чата после первого обмена.
     *
     * Условия:
     * - [ChatConfig.autoNameEnabled] = true
     * - Имя ещё не сгенерировано
     * - Есть хотя бы одно сообщение пользователя и один ответ ассистента
     *
     * При падении [ChatNameGenerator] имя остаётся прежним (graceful degradation).
     *
     * @param session текущая сессия
     * @return сессия с обновлённым именем (или исходная при ошибке)
     */
    suspend fun maybeAutoName(session: ChatSession): ChatSession {
        if (!config.autoNameEnabled) return session
        if (session.metadata.nameGenerated) return session

        val userMessages = session.messages.filterIsInstance<ChatMessage.User>()
        val assistantMessages = session.messages.filterIsInstance<ChatMessage.Assistant>()
        if (userMessages.isEmpty() || assistantMessages.isEmpty()) return session

        val result = runCatching {
            nameGenerator.generate(session.messages)
        }

        return result
            .onFailure { error ->
                System.err.println("[$TAG] ChatNameGenerator failed (graceful degradation): ${error.message}")
            }
            .getOrNull()
            ?.getOrNull()
            ?.let { name ->
                try {
                    val renamed = session.rename(name, generated = true)
                    repository.save(renamed).onFailure { error ->
                        System.err.println("[$TAG] Failed to save auto-named session: ${error.message}")
                    }
                    renamed
                } catch (e: Exception) {
                    System.err.println("[$TAG] Failed to rename session: ${e.message}")
                    session
                }
            }
            ?: session
    }

    /**
     * Очищает историю сообщений в чат-сессии, сохраняя TaskState.
     *
     * @param sessionId идентификатор чат-сессии
     * @throws IllegalStateException если сессия не найдена
     */
    suspend fun clearHistory(sessionId: UUID) {
        val session = repository.loadById(sessionId)
            .onFailure { error ->
                System.err.println("[$TAG] Failed to load session $sessionId: ${error.message}")
            }
            .getOrNull()
            ?: throw IllegalStateException("Chat session not found: $sessionId")

        val cleared = session.copy(messages = emptyList())
        repository.save(cleared).onFailure { error ->
            System.err.println("[$TAG] Failed to clear history $sessionId: ${error.message}")
        }
    }

    // ──── Private helpers ────

    /**
     * Архивирует текущую активную сессию, если она существует.
     */
    private suspend fun archiveActiveIfExists() {
        val active = repository.loadActive()
            .onFailure { error ->
                System.err.println("[$TAG] Failed to load active session: ${error.message}")
            }
            .getOrNull()
            ?: return

        repository.archiveSession(active.metadata.id).onFailure { error ->
            System.err.println("[$TAG] Failed to archive session ${active.metadata.id}: ${error.message}")
        }
    }

    /**
     * Активирует последнюю неархивированную сессию или создаёт новую.
     */
    private suspend fun activateLastOrCreate() {
        val sessions = listSessions()
        val lastNonArchived = sessions
            .filter { !it.metadata.archived }
            .maxByOrNull { it.metadata.updatedAt }

        if (lastNonArchived != null) {
            repository.setActive(lastNonArchived.metadata.id).onFailure { error ->
                System.err.println("[$TAG] Failed to activate session ${lastNonArchived.metadata.id}: ${error.message}")
            }
        } else {
            createSession()
        }
    }
}
