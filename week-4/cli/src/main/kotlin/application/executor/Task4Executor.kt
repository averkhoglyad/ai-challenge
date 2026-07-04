package io.averkhogliad.ai.challenge.week4.cli.application.executor

import io.averkhogliad.ai.challenge.week4.cli.application.DialogService
import io.averkhogliad.ai.challenge.week4.cli.domain.Prompt
import io.averkhogliad.ai.challenge.week4.cli.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week4.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.SessionLevel
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskId

/**
 * Executor для Task 4.
 *
 * ## Архитектурная роль
 * - **Application Layer** — делегирует LLM-запросы в [DialogService]
 * - **Не зависит от UI** (CLI, Mordant)
 *
 * Task 4 расширяет RAG из Task 2/3 добавлением цитирования найденных
 * фрагментов, указанием источников и механизмами анти-галлюцинаций.
 * Обеспечивает проверяемость ответов LLM через ссылки на исходные документы.
 *
 * RAG-интеграция выполняется на уровне [UserInputFlowHandler], который
 * использует [RagQueryProcessor] напрямую, имея доступ к [CliState.ragState].
 *
 * @param dialogService сервис диалога с LLM
 */
class Task4Executor(
    private val dialogService: DialogService
) : TaskExecutor {

    override val taskId: TaskId = TaskId("4")

    override val metadata: TaskMetadata = TaskMetadata(
        id = taskId,
        title = "Task 4: RAG с цитатами, источниками и анти-галлюцинациями",
        description = "LLM-диалог с Retrieval-Augmented Generation (RAG), " +
                "расширенный цитированием найденных фрагментов, " +
                "указанием источников и механизмами анти-галлюцинаций. " +
                "Гарантирует проверяемость ответов через ссылки на исходные документы.",
        availableCommands = listOf(
            ":rag", ":rag status", ":rag list",
            ":rag citations", ":rag sources",
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
