package io.averkhogliad.ai.challenge.week0.application.executor

import io.averkhogliad.ai.challenge.week0.domain.Prompt
import io.averkhogliad.ai.challenge.week0.domain.TaskId
import io.averkhogliad.ai.challenge.week0.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week0.domain.TaskResult
import io.averkhogliad.ai.challenge.week0.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week0.domain.service.LlmPort

/**
 * Executor для Task 1: простой chat-completion.
 *
 * Оркестрирует вызов [LlmPort.chat] — минимальная задача:
 * один запрос, один ответ.
 *
 * ## Архитектурные решения
 * - **Делегирует бизнес-логику** [LlmPort.chat] — domain port
 * - **Не зависит от UI** — не содержит Mordant/терминал
 * - **Обрабатывает ошибки** — исключения преобразуются в [TaskResult.Error]
 * - **Конфигурация прозрачна** — [TaskExecutionConfig] передаётся как есть
 *
 * Executor зависит только от domain port [LlmPort] и domain моделей.
 */
class Task1Executor(
    private val llmPort: LlmPort
) : TaskExecutor {

    override val taskId: TaskId = TaskId(1)

    override val metadata: TaskMetadata = TaskMetadata(
        id = taskId,
        title = "Task 1: простой chat-completion (single prompt)",
        description = "Отправляет одиночный промпт модели и возвращает ответ."
    )

    override suspend fun execute(prompt: Prompt, config: TaskExecutionConfig): TaskResult {
        return try {
            llmPort.chat(prompt, config)
        } catch (e: Exception) {
            TaskResult.Error(
                message = "Task 1 execution failed: ${e.message}",
                cause = e
            )
        }
    }
}
