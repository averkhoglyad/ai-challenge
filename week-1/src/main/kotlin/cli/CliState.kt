package io.averkhogliad.ai.challenge.week1.cli

import io.averkhogliad.ai.challenge.week1.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week1.domain.model.DialogId

/**
 * Иммутабельное состояние CLI (Imperative Shell).
 *
 * Содержит всю мутабельную информацию, необходимую для REPL-цикла:
 * - Текущая выбранная задача ([currentTaskId])
 * - Текущий активный диалог ([currentDialogId]) — для Task 2
 * - Конфигурация выполнения ([executionConfig])
 * - Флаг активности REPL ([isRunning])
 *
 * Все поля имеют разумные значения по умолчанию.
 */
data class CliState(
    /** ID текущей задачи (null — этап выбора задачи) */
    val currentTaskId: Int? = null,

    /** ID текущего активного диалога (null — диалог не выбран, для Task 2) */
    val currentDialogId: DialogId? = null,

    /** Общая конфигурация выполнения (temperature, maxTokens, stopSequences, modelId) */
    val executionConfig: TaskExecutionConfig = TaskExecutionConfig(),

    /** Флаг работы REPL-цикла */
    val isRunning: Boolean = true
)
