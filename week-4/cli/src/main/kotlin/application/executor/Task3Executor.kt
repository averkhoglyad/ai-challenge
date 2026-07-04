package io.averkhogliad.ai.challenge.week4.cli.application.executor

import io.averkhogliad.ai.challenge.week4.cli.application.DialogService
import io.averkhogliad.ai.challenge.week4.cli.domain.Prompt
import io.averkhogliad.ai.challenge.week4.cli.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week4.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.SessionLevel
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskId

/**
 * Executor для Task 3.
 *
 * ## Архитектурная роль
 * - **Application Layer** — делегирует LLM-запросы в [DialogService]
 * - **Не зависит от UI** (CLI, Mordant)
 *
 * Task 3 расширяет RAG из Task 2 добавлением реранкинга (cross-encoder/LLM)
 * и фильтрации (по стратегии индексации, размеру чанков).
 *
 * RAG-интеграция выполняется на уровне [UserInputFlowHandler], который
 * использует [RagQueryProcessor] напрямую, имея доступ к [CliState.ragState].
 *
 * @param dialogService сервис диалога с LLM
 */
class Task3Executor(
    private val dialogService: DialogService
) : TaskExecutor {

    override val taskId: TaskId = TaskId("3")

    override val metadata: TaskMetadata = TaskMetadata(
        id = taskId,
        title = "Task 3: RAG с реранкингом и фильтрацией",
        description = "LLM-диалог с Retrieval-Augmented Generation (RAG), " +
                "расширенный реранкингом (cross-encoder/LLM) и фильтрацией " +
                "(по стратегии индексации, размеру чанков). " +
                "Позволяет сравнивать качество базового и продвинутого RAG.",
        availableCommands = listOf(
            ":rag", ":rag status", ":rag list",
            ":rag rerank", ":rag filter",
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
