package io.averkhogliad.ai.challenge.week4.cli.application.executor

import io.averkhogliad.ai.challenge.week4.cli.application.DialogService
import io.averkhogliad.ai.challenge.week4.cli.domain.Prompt
import io.averkhogliad.ai.challenge.week4.cli.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week4.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.SessionLevel
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskId

/**
 * Executor для Task 1: AI Agent с LLM.
 *
 * ## Архитектурная роль
 * - **Application Layer** — оркестрация диалога с LLM через [DialogService]
 * - **Не зависит от UI** (CLI, Mordant)
 * - **Делегирует бизнес-логику** [DialogService]
 *
 * ## Функциональность
 * - Интеллектуальный агент с использованием LLM для общения
 * - Поддержка конфигурации (temperature, maxTokens, stopSequences)
 * - Интеграция с MCP-инструментами для расширенной функциональности
 * - Поддержка внутренних builtin-инструментов
 * - Поддерживает контролируемые переходы состояний FSM
 *
 * @param dialogService сервис диалога с LLM
 */
class Task1Executor(
    private val dialogService: DialogService
) : TaskExecutor {

    override val taskId: TaskId = TaskId("1")

    override val metadata: TaskMetadata = TaskMetadata(
        id = taskId,
        title = "Task 1: Индексация для LLM Embedding",
        description = "Индексация документов для LLM Embedding.",
        availableCommands = listOf(
            ":temp <value>", ":maxtokens <n>", ":params",
            ":plan", ":status", ":clear",
            ":back"
        )
    )

    override suspend fun execute(prompt: Prompt, config: TaskExecutionConfig): TaskResult {
        // Делегируем в DialogService.chat() — основную точку входа для общения
        return dialogService.chat(
            userInput = prompt.value,
            level = SessionLevel.TASK_LIST,
            taskId = null
        )
    }
}
