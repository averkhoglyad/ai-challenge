package io.averkhogliad.ai.challenge.week2.domain.service

import io.averkhogliad.ai.challenge.week2.domain.model.Task
import io.averkhogliad.ai.challenge.week2.domain.model.TaskId

/**
 * Port (интерфейс) для персистентного хранения задач.
 *
 * Определяет контракт для infrastructure-слоя (SQLite, PostgreSQL, etc.).
 * Domain-слой зависит только от этого интерфейса, реализуя принцип
 * Dependency Inversion (DIP).
 *
 * ## Архитектурная роль
 * - **Hexagonal Architecture** — port для persistence
 * - **Domain определяет контракт** — infrastructure реализует
 * - **Suspend функции** — поддержка асинхронных операций I/O
 *
 * ## Реализации
 * - [SqliteTaskRepository][io.averkhogliad.ai.challenge.week2.infrastructure.persistence.SqliteTaskRepository] — SQLite через JDBC
 * - InMemoryTaskRepository — для тестирования
 */
interface TaskRepository {
    /**
     * Сохраняет задачу в хранилище.
     *
     * Если задача с таким ID уже существует — обновляет её.
     * Иначе — создаёт новую запись.
     *
     * @param task задача для сохранения
     */
    suspend fun save(task: Task)

    /**
     * Находит задачу по идентификатору.
     *
     * @param id идентификатор задачи
     * @return найденная задача или null, если не найдена
     */
    suspend fun findById(id: TaskId): Task?

    /**
     * Возвращает список всех задач.
     *
     * @return список всех задач
     */
    suspend fun findAll(): List<Task>

    /**
     * Удаляет задачу по идентификатору.
     *
     * Если задача не существует — операция завершается успешно (идемпотентность).
     *
     * @param id идентификатор задачи для удаления
     */
    suspend fun delete(id: TaskId)

    /**
     * Проверяет существование задачи по идентификатору.
     *
     * @param id идентификатор задачи
     * @return true, если задача существует, иначе false
     */
    suspend fun exists(id: TaskId): Boolean
}
