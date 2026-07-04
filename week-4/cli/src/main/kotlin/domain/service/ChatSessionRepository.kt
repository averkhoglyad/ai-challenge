package io.averkhogliad.ai.challenge.week4.cli.domain.service

import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatSession
import java.util.*

/**
 * Порт репозитория для персистентности чат-сессий.
 *
 * ## Архитектурная роль
 * - **Domain Port** — абстракция для персистентности в гексагональной архитектуре
 * - **Inversion of Control** — domain определяет контракт, infrastructure реализует
 *
 * ## Контракт
 * Реализация отвечает за сохранение, загрузку, поиск и удаление [ChatSession]
 * в постоянном хранилище (БД, файл и т.д.).
 */
interface ChatSessionRepository {

    /**
     * Сохраняет (создаёт или обновляет) чат-сессию.
     *
     * @param session сессия для сохранения
     * @return [Result] с сохранённой сессией
     */
    suspend fun save(session: ChatSession): Result<ChatSession>

    /**
     * Загружает активную чат-сессию.
     *
     * @return [Result] с активной сессией или `null`, если активной нет
     */
    suspend fun loadActive(): Result<ChatSession?>

    /**
     * Загружает чат-сессию по идентификатору.
     *
     * @param id идентификатор сессии
     * @return [Result] с найденной сессией или `null`
     */
    suspend fun loadById(id: UUID): Result<ChatSession?>

    /**
     * Возвращает список всех чат-сессий.
     *
     * @return [Result] со списком всех сессий
     */
    suspend fun listSessions(): Result<List<ChatSession>>

    /**
     * Устанавливает указанную сессию как активную.
     * Предыдущая активная сессия деактивируется.
     *
     * @param id идентификатор сессии для активации
     * @return [Result] с `Unit` при успехе
     */
    suspend fun setActive(id: UUID): Result<Unit>

    /**
     * Архивирует чат-сессию.
     *
     * @param id идентификатор сессии
     * @return [Result] с `Unit` при успехе
     */
    suspend fun archiveSession(id: UUID): Result<Unit>

    /**
     * Удаляет чат-сессию.
     *
     * @param id идентификатор сессии
     * @return [Result] с `Unit` при успехе
     */
    suspend fun deleteSession(id: UUID): Result<Unit>
}
