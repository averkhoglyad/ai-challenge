package io.averkhogliad.ai.challenge.week4.cli.domain.model

/**
 * Контекст исполнения для builtin tools.
 *
 * Содержит информацию о текущей открытой задаче.
 * LLM не знает технических идентификаторов — контекст определяется автоматически.
 *
 * ## Архитектурная роль
 * - **Domain Model** — чистый value object
 * - **Immutable** — data class, изменения через copy()
 *
 * @property currentTask Текущая открытая задача (null, если нет открытой)
 */
data class BuiltinToolContext(
    val currentTask: Task? = null
) {
    companion object {
        /** Пустой контекст (нет открытой задачи) */
        val EMPTY = BuiltinToolContext()
    }

    /** Есть ли открытая задача */
    val hasOpenTask: Boolean get() = currentTask != null
}
