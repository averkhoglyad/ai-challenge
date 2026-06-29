package io.averkhogliad.ai.challenge.week3.cli.application.executor

import io.averkhogliad.ai.challenge.week3.cli.application.DialogService
import io.averkhogliad.ai.challenge.week3.cli.domain.Prompt
import io.averkhogliad.ai.challenge.week3.cli.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week3.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week3.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week3.cli.domain.model.SessionLevel
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskId

/**
 * Executor для Task 5: Smart Task Planner.
 *
 * ## Архитектурная роль
 * - **Application Layer** — оркестрация диалога с LLM через [DialogService]
 * - **Не зависит от UI** (CLI, Mordant)
 * - **Делегирует бизнес-логику** [DialogService]
 *
 * ## Функциональность
 * - Реализация Smart Task Planner: интеллектуальное планирование задач
 *   с использованием LLM для генерации, приоритизации и декомпозиции задач
 * - Автоматическое создание планов выполнения на основе пользовательского ввода
 * - Поддержка управления задачами, шагами, фактами и общение с LLM
 * - Поддерживает контролируемые переходы состояний FSM
 * - Интеграция с MCP-инструментами для расширенной функциональности
 *
 * @param dialogService сервис диалога с LLM
 */
class Task5Executor(
    private val dialogService: DialogService
) : TaskExecutor {

    override val taskId: TaskId = TaskId("5")

    override val metadata: TaskMetadata = TaskMetadata(
        id = taskId,
        title = "Task 5: Smart Task Planner",
        description = "Smart Task Planner: интеллектуальное планирование задач с использованием LLM.",
        availableCommands = listOf(
            ":create-event <date>", ":notes [limit]",
            ":add <text>", ":list", ":edit <id> <text>", ":drop <id>",
            ":open <id>", ":close", ":cancel", ":back",
            ":plan", ":status", ":clear",
            ":temp <value>", ":maxtokens <n>", ":params"
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
