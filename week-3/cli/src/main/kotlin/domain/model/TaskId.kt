package io.averkhogliad.ai.challenge.week3.cli.domain.model

/**
 * Value object для уникального идентификатора задачи.
 *
 * Использует [@JvmInline](https://kotlinlang.org/docs/inline-classes.html)
 * для zero-overhead абстракции. Гарантирует типобезопасность при работе
 * с идентификаторами задач.
 *
 * @property value строковое представление идентификатора (UUID)
 */
@JvmInline
value class TaskId(val value: String) {
    init {
        require(value.isNotBlank()) { "TaskId cannot be blank" }
    }

    override fun toString(): String = value
}
