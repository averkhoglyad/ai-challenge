package io.averkhogliad.ai.challenge.week0.cli

import io.averkhogliad.ai.challenge.week0.domain.config.Task3Config
import io.averkhogliad.ai.challenge.week0.domain.config.TaskExecutionConfig

/**
 * Иммутабельное состояние CLI (Imperative Shell).
 *
 * Содержит всю мутабельную информацию, необходимую для REPL-цикла:
 * - Текущая выбранная задача ([currentTaskId])
 * - Конфигурация выполнения ([executionConfig]), включая [Task3Config]
 * - Флаг активности REPL ([isRunning])
 * - Параметры Task5 (бенчмарк моделей)
 *
 * Все поля имеют разумные значения по умолчанию.
 * Task3-специфичные параметры (mode, step, role, experts, summary)
 * хранятся в [executionConfig.task3], а не как отдельные поля CliState.
 */
data class CliState(
    /** ID текущей задачи (null — этап выбора задачи) */
    val currentTaskId: Int? = null,

    /** Общая конфигурация выполнения (temperature, maxTokens, stopSequences, modelId, task3) */
    val executionConfig: TaskExecutionConfig = TaskExecutionConfig(),

    /** Флаг работы REPL-цикла */
    val isRunning: Boolean = true,

    // ═══════════════════════════════════════════════════════════════
    // Параметры Task5 (бенчмарк моделей)
    // ═══════════════════════════════════════════════════════════════

    /** Индексы выбранных моделей (1-based) */
    val task5SelectedModels: List<Int> = emptyList()
)
