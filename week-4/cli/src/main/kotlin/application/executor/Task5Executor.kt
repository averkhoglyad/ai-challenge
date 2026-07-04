package io.averkhogliad.ai.challenge.week4.cli.application.executor

import io.averkhogliad.ai.challenge.week4.cli.application.chat.ChatExecutor
import io.averkhogliad.ai.challenge.week4.cli.application.chat.ChatSessionManager
import io.averkhogliad.ai.challenge.week4.cli.domain.Prompt
import io.averkhogliad.ai.challenge.week4.cli.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week4.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskId

/**
 * Executor для Task 5: Мини-чат с RAG и персистентной памятью.
 *
 * Тонкая обёртка над [ChatExecutor] + [ChatSessionManager].
 * Оркестрирует полный pipeline диалога:
 * extract → apply → build prompt → RAG → save.
 *
 * ## Архитектурная роль
 * - **Application Layer** — делегирует вызовы в [ChatExecutor]
 * - **Не зависит от UI** (CLI, Mordant)
 *
 * При отсутствии активной сессии создаёт новую через [ChatSessionManager].
 *
 * @param chatExecutor оркестратор одного хода диалога (pipeline с двумя LLM-вызовами)
 * @param chatSessionManager менеджер множественных чат-сессий
 */
class Task5Executor(
    private val chatExecutor: ChatExecutor,
    private val chatSessionManager: ChatSessionManager
) : TaskExecutor {

    override val taskId: TaskId = TaskId("5")

    override val metadata: TaskMetadata = TaskMetadata(
        id = taskId,
        title = "Task 5: Мини-чат с RAG и персистентной памятью",
        description = "Мини-чат с Retrieval-Augmented Generation (RAG) " +
                "и долговременной памятью (LTM). " +
                "Сохраняет контекст диалога, обогащает промпты релевантными " +
                "фактами из памяти и истории, обеспечивает связность " +
                "многошаговых диалогов через персистентное хранение состояния.",
        availableCommands = listOf(
            ":rag", ":rag status", ":rag list",
            ":rag citations", ":rag sources",
            ":memory", ":memory status", ":memory search <query>",
            ":temp <value>", ":maxtokens <n>", ":params",
            ":plan", ":status", ":clear",
            ":back"
        )
    )

    override suspend fun execute(prompt: Prompt, config: TaskExecutionConfig): TaskResult {
        return try {
            // Получить или создать активную сессию
            val activeSession = chatSessionManager.getActiveSession()
                ?: chatSessionManager.createSession()

            // Выполнить pipeline через ChatExecutor
            val result = chatExecutor.execute(
                userInput = prompt.value,
                sessionId = activeSession.metadata.id,
                executionConfig = config
            )

            TaskResult.Success(
                content = result.answer,
                metadata = mapOf(
                    "taskStateUpdated" to result.taskStateUpdated.toString(),
                    "citations" to result.citations.size.toString()
                )
            )
        } catch (e: Exception) {
            TaskResult.Error("Chat execution failed: ${e.message}", e)
        }
    }
}
