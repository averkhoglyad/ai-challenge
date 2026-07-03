package io.averkhogliad.ai.challenge.week4.cli.application.executor

import io.averkhogliad.ai.challenge.week4.cli.application.DialogService
import io.averkhogliad.ai.challenge.week4.cli.domain.Prompt
import io.averkhogliad.ai.challenge.week4.cli.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week4.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.SessionLevel
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskId

/**
 * Executor для Task 2.
 *
 * ## Архитектурная роль
 * - **Application Layer** — делегирует LLM-запросы в [DialogService]
 * - **Не зависит от UI** (CLI, Mordant)
 *
 * RAG-интеграция выполняется на уровне [UserInputFlowHandler], который
 * использует [RagQueryProcessor] напрямую, имея доступ к [CliState.ragState].
 *
 * @param dialogService сервис диалога с LLM
 */
class Task2Executor(
    private val dialogService: DialogService
) : TaskExecutor {

    override val taskId: TaskId = TaskId("2")

    override val metadata: TaskMetadata = TaskMetadata(
        id = taskId,
        title = "Task 2: LLM-диалог с поддержкой RAG",
        description = "LLM-диалог с поддержкой Retrieval-Augmented Generation (RAG). " +
                "При включённом RAG выполняет векторный поиск по индексам, " +
                "собирает augmented-промпт и отправляет в LLM с контекстом. " +
                "При выключенном RAG или ошибках — обычный LLM-запрос.",
        availableCommands = listOf(
            ":rag", ":rag status", ":rag list",
            ":temp <value>", ":maxtokens <n>", ":params",
            ":plan", ":status", ":clear",
            ":back"
        )
    )

    override suspend fun execute(prompt: Prompt, config: TaskExecutionConfig): TaskResult {
        return dialogService.chat(
            userInput = prompt.value,
            level = SessionLevel.TASK_LIST,
            taskId = null
        )
    }
}
