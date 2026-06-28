package io.averkhogliad.ai.challenge.week3.cli.application.executor

import io.averkhogliad.ai.challenge.week3.cli.application.DialogService
import io.averkhogliad.ai.challenge.week3.cli.domain.Prompt
import io.averkhogliad.ai.challenge.week3.cli.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week3.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week3.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week3.cli.domain.model.SessionLevel
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskId

/**
 * Executor для Task 3: Календарь событий и уведомления.
 *
 * ## Архитектурная роль
 * - **Application Layer** — оркестрация диалога с LLM через [DialogService]
 * - **Не зависит от UI** (CLI, Mordant)
 * - **Делегирует бизнес-логику** [DialogService]

 *
 * ## Функциональность
 * - Интеграция с календарём событий (events-server) и сервером уведомлений (notification-server)
 * - Позволяет привязывать задачи к датам, создавать события в календаре и получать уведомления
 * - Поддерживает управление задачами, шагами, фактами и общение с LLM
 * - Поддерживает возможность генерить план выполнения
 * - Поддерживает контролируемые переходы состояний FSM
 *
 * @param dialogService сервис диалога с LLM

 */
class Task3Executor(
    private val dialogService: DialogService
) : TaskExecutor {


    override val taskId: TaskId = TaskId("3")

    override val metadata: TaskMetadata = TaskMetadata(
        id = taskId,
        title = "Task 3: Календарь событий и уведомления",
        description = "Интеграция с календарём событий (events-server) и сервером уведомлений (notification-server). " +
                "Позволяет привязывать задачи к датам, создавать события в календаре и получать уведомления.",
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
