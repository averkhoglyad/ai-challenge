package io.averkhogliad.ai.challenge.week2.cli

import io.averkhogliad.ai.challenge.week2.domain.config.TaskExecutionConfig

/**
 * Иммутабельное состояние CLI (Imperative Shell).
 *
 * Содержит всю мутабельную информацию, необходимую для REPL-цикла:
 * - Текущая выбранная задача ([currentTaskId])
 * - ID текущего активного диалога ([currentDialogId])
 * - Конфигурация выполнения ([executionConfig])
 * - Флаг активности REPL ([isRunning])
 * - ID текущей открытой задачи todo-менеджера ([currentTodoTaskId])
 * - Режим просмотра ([viewMode])
 *
 * Все поля имеют разумные значения по умолчанию.
 */
data class CliState(
    /** ID текущей задачи (null — этап выбора задачи) */
    val currentTaskId: Int? = null,

    /** ID текущего активного диалога (null — диалог не выбран) */
    val currentDialogId: String? = null,

    /** Общая конфигурация выполнения (temperature, maxTokens, stopSequences, modelId) */
    val executionConfig: TaskExecutionConfig = TaskExecutionConfig(),

    /** Флаг работы REPL-цикла */
    val isRunning: Boolean = true,

    /** ID текущей открытой задачи todo-менеджера (null — задача не открыта) */
    val currentTodoTaskId: String? = null,

    /** Режим просмотра CLI */
    val viewMode: ViewMode = ViewMode.LIST,

    /**
     * Флаг режима списка задач (без конкретной открытой задачи).
     * Используется для двухуровневой навигации: задача → список задач → главное меню.
     * true — показывается список задач, доступны команды управления задачами.
     */
    val taskListMode: Boolean = false
)

/**
 * Режим просмотра CLI.
 */
enum class ViewMode {
    /** Просмотр списка задач */
    LIST,

    /** Просмотр детальной информации о задаче */
    TASK
}
