package io.averkhogliad.ai.challenge.week2.domain.model

import java.time.Instant

/**
 * Доменная модель задачи в системе управления задачами.
 *
 * ## Архитектурная роль
 * - **Domain Model** — центральная сущность предметной области
 * - **Immutable** — все изменения возвращают новый экземпляр
 * - **Rich Domain Model** — содержит бизнес-логику изменения статусов
 *
 * ## Свойства
 * - [id] — уникальный идентификатор задачи
 * - [title] — название задачи (не может быть пустым)
 * - [status] — текущий статус задачи
 * - [createdAt] — время создания задачи
 * - [updatedAt] — время последнего обновления задачи
 *
 * ## Бизнес-логика
 * - [close()] — завершает задачу
 * - [cancel()] — отменяет задачу
 * - [updateTitle()] — обновляет название задачи
 */
data class Task(
    val id: TaskId,
    val title: String,
    val status: TaskStatus,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(title.isNotBlank()) { "Task title cannot be blank" }
    }

    /**
     * Проверяет, открыта ли задача.
     */
    fun isOpen(): Boolean = status == TaskStatus.OPEN

    /**
     * Проверяет, завершена ли задача.
     */
    fun isClosed(): Boolean = status == TaskStatus.CLOSED

    /**
     * Проверяет, отменена ли задача.
     */
    fun isCancelled(): Boolean = status == TaskStatus.CANCELLED

    /**
     * Завершает задачу.
     *
     * @return новая копия задачи со статусом CLOSED и обновлённым updatedAt
     */
    fun close(): Task = copy(status = TaskStatus.CLOSED, updatedAt = Instant.now())

    /**
     * Отменяет задачу.
     *
     * @return новая копия задачи со статусом CANCELLED и обновлённым updatedAt
     */
    fun cancel(): Task = copy(status = TaskStatus.CANCELLED, updatedAt = Instant.now())

    /**
     * Обновляет название задачи.
     *
     * @param newTitle новое название задачи (не может быть пустым)
     * @return новая копия задачи с обновлённым названием и updatedAt
     * @throws IllegalArgumentException если newTitle пустой
     */
    fun updateTitle(newTitle: String): Task {
        require(newTitle.isNotBlank()) { "Task title cannot be blank" }
        return copy(title = newTitle, updatedAt = Instant.now())
    }
}
