package io.averkhogliad.ai.challenge.week2.domain.service

import io.averkhogliad.ai.challenge.week2.domain.model.TaskId
import io.averkhogliad.ai.challenge.week2.domain.model.TaskStep
import io.averkhogliad.ai.challenge.week2.domain.model.TaskStepId

/**
 * Порт репозитория для персистентности шагов задач.
 *
 * ## Архитектурная роль
 * - **Port** — абстракция для персистентности в гексагональной архитектуре
 * - **Domain Service** — определяет контракт для сохранения и поиска шагов задач
 *
 * ## Контракт
 * Реализация этого интерфейса отвечает за сохранение и загрузку шагов задач
 * из постоянного хранилища (БД, файл и т.д.).
 */
interface TaskStepRepository {

    /**
     * Сохраняет шаг задачи.
     *
     * Если шаг с таким ID уже существует — обновляет его.
     * Иначе — создаёт новую запись.
     *
     * @param step шаг для сохранения
     * @return сохранённый шаг (с возможным обновлением идентификатора)
     */
    fun save(step: TaskStep): TaskStep

    /**
     * Находит все шаги задачи, отсортированные по порядку.
     *
     * @param taskId идентификатор задачи
     * @return список шагов задачи, отсортированный по [TaskStep.order]
     */
    fun findByTaskId(taskId: TaskId): List<TaskStep>

    /**
     * Находит шаг по идентификатору.
     *
     * @param stepId идентификатор шага
     * @return найденный шаг или null, если не найден
     */
    fun findById(stepId: TaskStepId): TaskStep?

    /**
     * Удаляет шаг по идентификатору.
     *
     * @param stepId идентификатор шага для удаления
     * @return true, если шаг был удалён; false, если шаг не существовал
     */
    fun delete(stepId: TaskStepId): Boolean

    /**
     * Удаляет все шаги задачи.
     *
     * @param taskId идентификатор задачи
     * @return количество удалённых шагов
     */
    fun deleteByTaskId(taskId: TaskId): Int

    /**
     * Возвращает количество шагов задачи.
     *
     * @param taskId идентификатор задачи
     * @return количество шагов в задаче
     */
    fun countByTaskId(taskId: TaskId): Int
}
