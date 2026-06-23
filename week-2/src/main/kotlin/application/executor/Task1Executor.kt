package io.averkhogliad.ai.challenge.week2.application.executor

import io.averkhogliad.ai.challenge.week2.application.DialogService
import io.averkhogliad.ai.challenge.week2.domain.Prompt
import io.averkhogliad.ai.challenge.week2.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week2.domain.TaskResult
import io.averkhogliad.ai.challenge.week2.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week2.domain.model.SessionLevel
import io.averkhogliad.ai.challenge.week2.domain.model.TaskId
import io.averkhogliad.ai.challenge.week2.domain.service.MemoryService

/**
 * Executor для Task 1: CLI-ассистент с трёхуровневой моделью памяти.
 *
 * ## Архитектурная роль
 * - **Application Layer** — оркестрация диалога с LLM через [DialogService]
 * - **Не зависит от UI** (CLI, Mordant)
 * - **Делегирует бизнес-логику** [DialogService] и [MemoryService]
 *
 * ## Функциональность
 * - Диалоговый ассистент с трёхуровневой моделью памяти (STM/WM/LTM)
 * - Поддерживает управление задачами, шагами, фактами и общение с LLM
 *
 * @param dialogService сервис диалога с LLM
 * @param memoryService сервис управления памятью (не используется напрямую, передан для симметрии)
 */
class Task1Executor(
    private val dialogService: DialogService,
    private val memoryService: MemoryService
) : TaskExecutor {

    override val taskId: TaskId = TaskId("1")

    override val metadata: TaskMetadata = TaskMetadata(
        id = taskId,
        title = "Task 1: CLI-ассистент",
        description = "Диалоговый ассистент с трёхуровневой моделью памяти (STM/WM/LTM). " +
                "Поддерживает управление задачами, шагами, фактами и общение с LLM.",
        availableCommands = listOf(
            ":add <text>", ":list", ":edit <id> <text>", ":drop <id>",
            ":open <id>", ":close", ":cancel", ":back",
            ":step-add <text>", ":step-list", ":step-done <id>",
            ":ctx-save <text>", ":ctx-list", ":ctx-forget <id>",
            ":plan <title>", ":status", ":clear",
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
