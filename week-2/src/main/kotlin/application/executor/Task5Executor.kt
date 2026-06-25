package io.averkhogliad.ai.challenge.week2.application.executor

import io.averkhogliad.ai.challenge.week2.application.DialogService
import io.averkhogliad.ai.challenge.week2.domain.Prompt
import io.averkhogliad.ai.challenge.week2.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week2.domain.TaskResult
import io.averkhogliad.ai.challenge.week2.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week2.domain.model.SessionLevel
import io.averkhogliad.ai.challenge.week2.domain.model.TaskId

/**
 * Executor для Task 5: CLI-ассистент с контролируемыми переходами состояний (копия Task 4).
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
 * - Поддерживает контролируемые переходы состояний FSM (Sprint 5)
 * - Идентичен Task 4, создан для демонстрации FSM-переходов
 *
 * @param dialogService сервис диалога с LLM

 */
class Task5Executor(
    private val dialogService: DialogService
) : TaskExecutor {


    override val taskId: TaskId = TaskId("5")

    override val metadata: TaskMetadata = TaskMetadata(
        id = taskId,
        title = "Task 5: CLI-ассистент с FSM",
        description = "Диалоговый ассистент с трёхуровневой моделью памяти (STM/WM/LTM). " +
                "Поддерживает управление задачами, шагами, фактами, общение с LLM, " +
                "возможность генерить план выполнения и контролируемые переходы состояний FSM.",
        availableCommands = listOf(
            ":add <text>", ":list", ":edit <id> <text>", ":drop <id>",
            ":open <id>", ":close", ":cancel", ":back",
            ":step-add <text>", ":step-list", ":step-done <id>",
            ":ctx-save <text>", ":ctx-list", ":ctx-forget <id>",
            ":plan <title>", ":goto [state]", ":status", ":clear",
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
