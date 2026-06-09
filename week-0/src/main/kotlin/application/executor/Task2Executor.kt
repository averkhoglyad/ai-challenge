package io.averkhogliad.ai.challenge.week0.application.executor

import io.averkhogliad.ai.challenge.week0.domain.Prompt
import io.averkhogliad.ai.challenge.week0.domain.TaskId
import io.averkhogliad.ai.challenge.week0.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week0.domain.TaskResult
import io.averkhogliad.ai.challenge.week0.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week0.domain.service.LlmPort
import io.averkhogliad.ai.challenge.week0.domain.service.ParameterValidator

/**
 * Executor для Task 2: расширенный chat-completion с параметрами.
 *
 * Оркестрирует валидацию параметров ([ParameterValidator]) и вызов [LlmPort.chat].
 *
 * ## Архитектурные решения
 * - **Валидация через [ParameterValidator]** — чистые функции без побочных эффектов.
 *   Каждый параметр конфига (temperature, maxTokens, stopSequences) валидируется
 *   отдельно перед выполнением запроса.
 * - **Делегирует бизнес-логику** [LlmPort.chat] — domain port
 * - **Не зависит от UI** — executor не управляет mutable state параметров (это ответственность UI/CLI)
 * - **Обрабатывает ошибки** — как ошибки валидации, так и ошибки API
 *   преобразуются в [TaskResult.Error]
 *
 * Executor содержит только оркестрацию: валидация → вызов API → результат.
 */
class Task2Executor(
    private val llmPort: LlmPort
) : TaskExecutor {

    override val taskId: TaskId = TaskId(2)

    override val metadata: TaskMetadata = TaskMetadata(
        id = taskId,
        title = "Task 2: расширенный chat-completion с параметрами",
        description = "Chat-completion с контролем параметров генерации: temperature, maxTokens, stop sequences.",
        availableCommands = listOf(":temp", ":maxTokens", ":stop", ":reset", ":params")
    )

    override suspend fun execute(prompt: Prompt, config: TaskExecutionConfig): TaskResult {
        // Валидация параметров уже выполнена в TaskExecutionConfig.init
        // (temperature в 0.0..2.0, maxTokens в 1..128000, stopSequences.size <= 4)

        // Выполнение запроса
        return try {
            llmPort.chat(prompt, config)
        } catch (e: Exception) {
            TaskResult.Error(
                message = "Task 2 execution failed: ${e.message}",
                cause = e
            )
        }
    }
}
