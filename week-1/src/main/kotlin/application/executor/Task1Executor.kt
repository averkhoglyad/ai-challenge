package io.averkhogliad.ai.challenge.week1.application.executor

import io.averkhogliad.ai.challenge.week1.domain.Prompt
import io.averkhogliad.ai.challenge.week1.domain.TaskId
import io.averkhogliad.ai.challenge.week1.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week1.domain.TaskResult
import io.averkhogliad.ai.challenge.week1.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week1.domain.service.Agent

/**
 * Executor для Task 1: простой chat-completion.
 *
 * Оркестрирует вызов [Agent.process] — минимальная задача:
 * один запрос, один ответ.
 *
 * ## Архитектурные решения
 * - **Делегирует бизнес-логику** [Agent] — domain service
 * - **Не зависит от UI** — не содержит Mordant/терминал
 * - **Обрабатывает ошибки** — исключения преобразуются в [TaskResult.Error]
 * - **Конфигурация прозрачна** — [TaskExecutionConfig] передаётся как есть
 *
 * Executor зависит только от domain service [Agent] и domain моделей.
 */
class Task1Executor(
    private val agent: Agent
) : TaskExecutor {

    override val taskId: TaskId = TaskId(1)

    override val metadata: TaskMetadata = TaskMetadata(
        id = taskId,
        title = "Task 1: простой chat-completion (single prompt)",
        description = "Отправляет одиночный промпт модели и возвращает ответ."
    )

    override suspend fun execute(prompt: Prompt, config: TaskExecutionConfig): TaskResult {
        return try {
            agent.process(prompt, config)
        } catch (e: Exception) {
            TaskResult.Error(
                message = "Task 1 execution failed: ${e.message}",
                cause = e
            )
        }
    }
}
