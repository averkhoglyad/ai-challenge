package io.averkhogliad.ai.challenge.week4.cli.domain.service

import io.averkhogliad.ai.challenge.week4.cli.domain.model.DialogSession
import io.averkhogliad.ai.challenge.week4.cli.domain.model.SessionId
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskId

/**
 * Порт репозитория для персистентности сессий диалога.
 *
 * ## Архитектурная роль
 * - **Port** — абстракция для персистентности в гексагональной архитектуре
 * - **Domain Service** — определяет контракт для сохранения и поиска сессий
 *
 * ## Контракт
 * Реализация этого интерфейса отвечает за сохранение и загрузку сессий диалога
 * из постоянного хранилища (БД, файл и т.д.).
 */
interface DialogSessionRepository {

    /**
     * Сохраняет сессию диалога.
     *
     * @param session сессия для сохранения
     * @return сохранённая сессия (с возможным обновлением идентификатора)
     */
    fun save(session: DialogSession): DialogSession

    /**
     * Находит сессию по идентификатору.
     *
     * @param id идентификатор сессии
     * @return найденная сессия или null, если не найдена
     */
    fun findById(id: SessionId): DialogSession?

    /**
     * Находит сессию по идентификатору задачи.
     *
     * @param taskId идентификатор задачи
     * @return найденная сессия или null, если не найдена
     */
    fun findByTaskId(taskId: TaskId): DialogSession?

    /**
     * Находит активную сессию (последнюю используемую).
     *
     * @return активная сессия или null, если нет активных сессий
     */
    fun findActiveSession(): DialogSession?

    /**
     * Удаляет сессию по идентификатору.
     *
     * @param id идентификатор сессии для удаления
     */
    fun delete(id: SessionId)
}
