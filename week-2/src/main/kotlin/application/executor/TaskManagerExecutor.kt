package io.averkhogliad.ai.challenge.week2.application.executor

import io.averkhogliad.ai.challenge.week2.domain.model.Task
import io.averkhogliad.ai.challenge.week2.domain.model.TaskId
import io.averkhogliad.ai.challenge.week2.domain.model.TaskStatus
import io.averkhogliad.ai.challenge.week2.domain.service.TaskRepository
import java.time.Instant
import java.util.*

/**
 * Executor для управления задачами в todo-менеджере.
 *
 * ## Архитектурная роль
 * - **Application Layer** — оркестрация операций с задачами
 * - **Состояние** — хранит [currentTaskId] для поддержки контекстных команд
 * - **Не зависит** от UI (CLI, Mordant)
 * - **Зависит только** от domain port [TaskRepository]
 *
 * ## Контекстные команды
 * Если команда не получает явный ID задачи, используется [currentTaskId].
 * Это позволяет работать с "текущей открытой задачей" без указания ID каждый раз.
 */
class TaskManagerExecutor(
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
     * @return созданная задача
     */
    suspend fun handleAddTask(title: String): Task {
        val taskId = TaskId(UUID.randomUUID().toString())
        val now = Instant.now()
        val task = Task(
            id = taskId,
            title = title,
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
    suspend fun handleListTasks(): List<Task> {
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
    suspend fun handleEditTask(id: TaskId?, title: String): Task {
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
    suspend fun handleDropTask(id: TaskId?) {
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
    suspend fun handleOpenTask(id: TaskId): Task {
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
    suspend fun handleCloseTask(id: TaskId?): Task {
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
    suspend fun handleCancelTask(id: TaskId?): Task {
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
     * Возвращается к списку задач (очищает [currentTaskId]).
     */
    fun handleBack() {
        currentTaskId = null
    }
}
