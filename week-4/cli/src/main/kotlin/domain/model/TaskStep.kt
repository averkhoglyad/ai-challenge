package io.averkhogliad.ai.challenge.week4.cli.domain.model

import java.time.Instant

/**
 * Value object для уникального идентификатора шага задачи.
 *
 * Использует [@JvmInline](https://kotlinlang.org/docs/inline-classes.html)
 * для zero-overhead абстракции. Гарантирует типобезопасность при работе
 * с идентификаторами шагов задач.
 *
 * @property value строковое представление идентификатора (UUID)
 */
@JvmInline
value class TaskStepId(val value: String) {
    init {
        require(value.isNotBlank()) { "TaskStepId cannot be blank" }
    }

    override fun toString(): String = value
}

/**
 * Доменная модель шага задачи в системе управления задачами.
 *
 * ## Архитектурная роль
 * - **Domain Model** — сущность предметной области, принадлежащая задаче
 * - **Immutable** — все изменения возвращают новый экземпляр
 * - **Rich Domain Model** — содержит бизнес-логику изменения статуса выполнения
 *
 * ## Свойства
 * - [id] — уникальный идентификатор шага
 * - [taskId] — ссылка на родительскую задачу
 * - [text] — описание шага (не может быть пустым)
 * - [isCompleted] — статус выполнения шага
 * - [order] — порядковый номер шага в рамках задачи (неотрицательный)
 * - [createdAt] — время создания шага
 *
 * ## Бизнес-логика
 * - [markCompleted()] — отмечает шаг как выполненный
 * - [markIncomplete()] — снимает отметку выполнения
 * - [updateText()] — обновляет текст шага
 */
data class TaskStep(
    val id: TaskStepId,
    val taskId: TaskId,
    val text: String,
    val isCompleted: Boolean,
    val order: Int,
    val createdAt: Instant
) {
    init {
        require(text.isNotBlank()) { "TaskStep text cannot be blank" }
        require(order >= 0) { "TaskStep order must be non-negative" }
    }

    /**
     * Отмечает шаг как выполненный.
     *
     * @return новая копия шага с [isCompleted] = true
     */
    fun markCompleted(): TaskStep = copy(isCompleted = true)

    /**
     * Снимает отметку выполнения с шага.
     *
     * @return новая копия шага с [isCompleted] = false
     */
    fun markIncomplete(): TaskStep = copy(isCompleted = false)

    /**
     * Обновляет текст шага.
     *
     * @param newText новый текст шага (не может быть пустым)
     * @return новая копия шага с обновлённым текстом
     * @throws IllegalArgumentException если [newText] пустой
     */
    fun updateText(newText: String): TaskStep {
        require(newText.isNotBlank()) { "TaskStep text cannot be blank" }
        return copy(text = newText)
    }
}
