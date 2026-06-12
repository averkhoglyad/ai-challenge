package io.averkhogliad.ai.challenge.week1.cli

import io.averkhogliad.ai.challenge.week1.domain.config.TaskExecutionConfig

/**
 * Иммутабельное состояние CLI (Imperative Shell).
 *
 * Содержит всю мутабельную информацию, необходимую для REPL-цикла:
 * - Текущая выбранная задача ([currentTaskId])
 * - Конфигурация выполнения ([executionConfig])
 * - Флаг активности REPL ([isRunning])
 *
 * Все поля имеют разумные значения по умолчанию.
 */
data class CliState(
    /** ID текущей задачи (null — этап выбора задачи) */
    val currentTaskId: Int? = null,

    /** Общая конфигурация выполнения (temperature, maxTokens, stopSequences, modelId) */
    val executionConfig: TaskExecutionConfig = TaskExecutionConfig(),

    /** Флаг работы REPL-цикла */
    val isRunning: Boolean = true
)
