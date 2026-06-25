package io.averkhogliad.ai.challenge.week2.application.executor

import io.averkhogliad.ai.challenge.week2.application.DialogService
import io.averkhogliad.ai.challenge.week2.domain.Prompt
import io.averkhogliad.ai.challenge.week2.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week2.domain.TaskResult
import io.averkhogliad.ai.challenge.week2.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week2.domain.model.SessionLevel
import io.averkhogliad.ai.challenge.week2.domain.model.TaskId

/**
 * Executor для Task 3: CLI-ассистент с трёхуровневой моделью памяти (копия Task 2).
 *
 * ## Архитектурная роль
 * - **Application Layer** — оркестрация диалога с LLM через [DialogService]
 * - **Не зависит от UI** (CLI, Mordant)
 * - **Делегирует бизнес-логику** [DialogService]

 *
 * ## Функциональность
 * - Диалоговый ассистент с трёхуровневой моделью памяти (STM/WM/LTM)
 * - Поддерживает управление задачами, шагами, фактами и общение с LLM
 * - Поддерживает возможность генерить план выполнения
 * - Идентичен Task 2, создан для демонстрации множественных задач
 *
 * @param dialogService сервис диалога с LLM

 */
class Task3Executor(
    private val dialogService: DialogService
) : TaskExecutor {


    override val taskId: TaskId = TaskId("3")

    override val metadata: TaskMetadata = TaskMetadata(
        id = taskId,
        title = "Task 3: CLI-ассистент",
        description = "Диалоговый ассистент с трёхуровневой моделью памяти (STM/WM/LTM). " +
                "Поддерживает управление задачами, шагами, фактами, общение с LLM " +
                "и возможность генерить план выполнения.",
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
