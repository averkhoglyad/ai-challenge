package io.averkhogliad.ai.challenge.week4.cli.application.chat

import io.averkhogliad.ai.challenge.week4.cli.domain.model.*
import io.averkhogliad.ai.challenge.week4.cli.domain.service.ChatSessionRepository
import java.util.*

/**
 * Ручное управление памятью задачи ([TaskState]) в рамках чат-сессии.
 *
 * Предоставляет атомарные операции (load → apply → save) для ручной
 * коррекции состояния задачи пользователем.
 *
 * ## Архитектурная роль
 * - **Application Layer** — оркестрация, координация портов
 * - **Не зависит** от UI и infrastructure
 * - **Зависит только** от domain-портов и моделей
 *
 * @property repository порт персистентности чат-сессий
 */
class TaskStateManager(
    private val repository: ChatSessionRepository,
    private val config: ChatConfig
) {
    companion object {
        private const val TAG = "TaskStateManager"
    }

    /**
     * Возвращает текущее состояние памяти задачи.
     *
     * @param sessionId идентификатор чат-сессии
     * @return текущий [TaskState]
     * @throws IllegalStateException если сессия не найдена
     */
    suspend fun getTaskState(sessionId: UUID): TaskState {
        val session = loadSession(sessionId)
        return session.taskState
    }

    /**
     * Устанавливает или заменяет цель задачи.
     *
     * Операция атомарна: load → apply → save.
     *
     * @param sessionId идентификатор чат-сессии
     * @param goal текст цели
     */
    suspend fun setGoal(sessionId: UUID, goal: String) {
        applyAndSave(sessionId, TaskStateDelta.SetGoal(goal))
    }

    /**
     * Добавляет новый термин с определением.
     *
     * Операция атомарна: load → apply → save.
     *
     * @param sessionId идентификатор чат-сессии
     * @param name имя термина
     * @param definition определение термина
     */
    suspend fun addTerm(sessionId: UUID, name: String, definition: String) {
        applyAndSave(sessionId, TaskStateDelta.AddTerm(name, definition))
    }

    /**
     * Удаляет термин по имени.
     *
     * Операция атомарна: load → apply → save.
     *
     * @param sessionId идентификатор чат-сессии
     * @param name имя термина для удаления
     */
    suspend fun removeTerm(sessionId: UUID, name: String) {
        applyAndSave(sessionId, TaskStateDelta.RemoveTerm(name))
    }

    /**
     * Добавляет ограничение.
     *
     * Операция атомарна: load → apply → save.
     *
     * @param sessionId идентификатор чат-сессии
     * @param constraint текст ограничения
     */
    suspend fun addConstraint(sessionId: UUID, constraint: String) {
        applyAndSave(sessionId, TaskStateDelta.AddConstraint(constraint))
    }

    /**
     * Удаляет ограничение по индексу.
     *
     * Операция атомарна: load → apply → save.
     *
     * @param sessionId идентификатор чат-сессии
     * @param index индекс ограничения в списке (0-based)
     */
    suspend fun removeConstraint(sessionId: UUID, index: Int) {
        applyAndSave(sessionId, TaskStateDelta.RemoveConstraint(index))
    }

    /**
     * Полностью сбрасывает состояние задачи к пустому.
     *
     * Операция атомарна: load → apply → save.
     *
     * @param sessionId идентификатор чат-сессии
     */
    suspend fun resetTaskState(sessionId: UUID) {
        applyAndSave(sessionId, TaskStateDelta.ResetAll)
    }

    // ──── Private helpers ────

    /**
     * Загружает сессию по идентификатору.
     *
     * @throws IllegalStateException если сессия не найдена
     */
    private suspend fun loadSession(sessionId: UUID): ChatSession {
        val result = repository.loadById(sessionId)
        return result
            .onFailure { error ->
                System.err.println("[$TAG] Failed to load session $sessionId: ${error.message}")
            }
            .getOrNull()
            ?: throw IllegalStateException("Chat session not found: $sessionId")
    }

    /**
     * Атомарно применяет дельту и сохраняет сессию.
     */
    private suspend fun applyAndSave(sessionId: UUID, delta: TaskStateDelta) {
        val session = loadSession(sessionId)
        val newState = session.taskState.applyDelta(
            delta,
            maxTerms = config.taskStateMaxTerms,
            maxConstraints = config.taskStateMaxConstraints,
            maxClarifiedFacts = config.maxClarifiedFacts
        )
        val updated = session.updateTaskState(newState)

        repository.save(updated).onFailure { error ->
            System.err.println("[$TAG] Failed to save session $sessionId: ${error.message}")
        }
    }
}
