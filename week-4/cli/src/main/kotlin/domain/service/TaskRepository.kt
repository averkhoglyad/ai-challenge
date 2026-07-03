package io.averkhogliad.ai.challenge.week4.cli.domain.service

import io.averkhogliad.ai.challenge.week4.cli.domain.model.Task
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskId
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskStep

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
 * - [SqliteTaskRepository][io.averkhogliad.ai.challenge.week4.cli.infrastructure.persistence.SqliteTaskRepository] — SQLite через JDBC
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

    /**
     * Сохраняет список шагов задачи в хранилище.
     *
     * Если у задачи уже были шаги — они заменяются новыми.
     * Иначе — создаются новые записи.
     *
     * @param taskId идентификатор задачи
     * @param steps список шагов для сохранения
     */
    suspend fun saveSteps(taskId: TaskId, steps: List<TaskStep>)

    /**
     * Возвращает список шагов задачи.
     *
     * @param taskId идентификатор задачи
     * @return список шагов задачи, отсортированный по порядку выполнения
     */
    suspend fun findStepsByTaskId(taskId: TaskId): List<TaskStep>

    /**
     * Привязывает событие к задаче.
     *
     * @param taskId идентификатор задачи
     * @param eventId идентификатор события
     * @param dueDate дата выполнения
     * @return Result.success если обновление прошло успешно, Result.failure при ошибке
     */
    suspend fun updateEvent(taskId: TaskId, eventId: java.util.UUID, dueDate: java.time.LocalDate): Result<Unit>

    /**
     * Отвязывает событие от задачи.
     *
     * @param taskId идентификатор задачи
     * @return Result.success если обновление прошло успешно, Result.failure при ошибке
     */
    suspend fun clearEvent(taskId: TaskId): Result<Unit>
}
