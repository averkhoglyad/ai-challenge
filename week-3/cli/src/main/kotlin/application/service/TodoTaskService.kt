package io.averkhogliad.ai.challenge.week3.cli.application.service

import io.averkhogliad.ai.challenge.week3.cli.domain.model.Task
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskId
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStatus
import io.averkhogliad.ai.challenge.week3.cli.domain.service.TaskRepository
import java.time.Instant
import java.util.*

/**
 * Service для управления задачами в todo-менеджере.
 *
 * ## Архитектурная роль
 * - **Application Layer** — Service для CRUD-операций над задачами
 * - **Состояние** — хранит [currentTaskId] для поддержки контекстных команд
 * - **Не зависит** от UI (CLI, Mordant)
 * - **Зависит только** от domain port [TaskRepository]
 *
 * ## Контекстные команды
 * Если команда не получает явный ID задачи, используется [currentTaskId].
 * Это позволяет работать с "текущей открытой задачей" без указания ID каждый раз.
 */
class TodoTaskService(
    private val taskRepository: TaskRepository
) {
    /**
     * Идентификатор текущей открытой задачи.
     * Используется для контекстных команд (US-2.9).
     */
    var currentTaskId: TaskId? = null
        private set

    /**
     * Создаёт новую задачу с указанным названием.
     *
     * @param title название задачи
     * @param description описание задачи (опционально)
     * @return созданная задача
     */
    suspend fun addTask(title: String, description: String? = null): Task {
        val taskId = TaskId(UUID.randomUUID().toString())
        val now = Instant.now()
        val task = Task(
            id = taskId,
            title = title,
            description = description?.takeIf { it.isNotBlank() },
            status = TaskStatus.OPEN,
            createdAt = now,
            updatedAt = now
        )
        taskRepository.save(task)
        return task
    }

    /**
     * Возвращает список всех задач.
     *
     * @return список всех задач
     */
    suspend fun listTasks(): List<Task> {
        return taskRepository.findAll()
    }

    /**
     * Редактирует название задачи.
     *
     * @param id идентификатор задачи (если null, используется [currentTaskId])
     * @param title новое название задачи
     * @return обновлённая задача
     * @throws IllegalStateException если не указан ID и нет открытой задачи
     * @throws IllegalArgumentException если задача не найдена
     */
    suspend fun editTask(id: TaskId?, title: String): Task {
        val taskId =
            id ?: currentTaskId ?: throw IllegalStateException("No task specified and no task is currently open")
        val task = taskRepository.findById(taskId) ?: throw IllegalArgumentException("Task not found: $taskId")
        val updatedTask = task.updateTitle(title)
        taskRepository.save(updatedTask)
        return updatedTask
    }

    /**
     * Удаляет задачу.
     *
     * @param id идентификатор задачи (если null, используется [currentTaskId])
     * @throws IllegalStateException если не указан ID и нет открытой задачи
     */
    suspend fun dropTask(id: TaskId?) {
        val taskId =
            id ?: currentTaskId ?: throw IllegalStateException("No task specified and no task is currently open")
        taskRepository.delete(taskId)
        if (currentTaskId == taskId) {
            currentTaskId = null
        }
    }

    /**
     * Открывает задачу и устанавливает её как текущую.
     *
     * @param id идентификатор задачи
     * @return открытая задача
     * @throws IllegalArgumentException если задача не найдена
     */
    suspend fun openTask(id: TaskId): Task {
        val task = taskRepository.findById(id) ?: throw IllegalArgumentException("Task not found: $id")
        currentTaskId = id
        return task
    }

    /**
     * Закрывает задачу.
     *
     * @param id идентификатор задачи (если null, используется [currentTaskId])
     * @return закрытая задача
     * @throws IllegalStateException если не указан ID и нет открытой задачи
     * @throws IllegalArgumentException если задача не найдена
     */
    suspend fun closeTask(id: TaskId?): Task {
        val taskId =
            id ?: currentTaskId ?: throw IllegalStateException("No task specified and no task is currently open")
        val task = taskRepository.findById(taskId) ?: throw IllegalArgumentException("Task not found: $taskId")
        val closedTask = task.close()
        taskRepository.save(closedTask)
        if (currentTaskId == taskId) {
            currentTaskId = null
        }
        return closedTask
    }

    /**
     * Отменяет задачу.
     *
     * @param id идентификатор задачи (если null, используется [currentTaskId])
     * @return отменённая задача
     * @throws IllegalStateException если не указан ID и нет открытой задачи
     * @throws IllegalArgumentException если задача не найдена
     */
    suspend fun cancelTask(id: TaskId?): Task {
        val taskId =
            id ?: currentTaskId ?: throw IllegalStateException("No task specified and no task is currently open")
        val task = taskRepository.findById(taskId) ?: throw IllegalArgumentException("Task not found: $taskId")
        val cancelledTask = task.cancel()
        taskRepository.save(cancelledTask)
        if (currentTaskId == taskId) {
            currentTaskId = null
        }
        return cancelledTask
    }

    /**
     * Обновляет описание задачи.
     *
     * @param id идентификатор задачи (если null, используется [currentTaskId])
     * @param description новое описание задачи
     * @return обновлённая задача
     * @throws IllegalStateException если не указан ID и нет открытой задачи
     * @throws IllegalArgumentException если задача не найдена
     */
    suspend fun updateDescription(id: TaskId?, description: String): Task {
        val taskId =
            id ?: currentTaskId ?: throw IllegalStateException("No task specified and no task is currently open")
        val task = taskRepository.findById(taskId) ?: throw IllegalArgumentException("Task not found: $taskId")
        val updatedTask = task.updateDescription(description)
        taskRepository.save(updatedTask)
        return updatedTask
    }

    /**
     * Возвращается к списку задач (очищает [currentTaskId]).
     */
    fun back() {
        currentTaskId = null
    }
}
