package io.averkhogliad.ai.challenge.week0.application.executor

import io.averkhogliad.ai.challenge.week0.domain.Prompt
import io.averkhogliad.ai.challenge.week0.domain.TaskId
import io.averkhogliad.ai.challenge.week0.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week0.domain.TaskResult
import io.averkhogliad.ai.challenge.week0.domain.config.Task3Config
import io.averkhogliad.ai.challenge.week0.domain.config.Task3Mode
import io.averkhogliad.ai.challenge.week0.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week0.domain.service.PromptEngineeringService

/**
 * Executor для Task 3: промпт-инжиниринг с модульными модификаторами.
 *
 * Оркестрирует вызов [PromptEngineeringService.execute] — делегирует всю
 * бизнес-логику промпт-инжиниринга (direct/experts режимы, step-by-step,
 * role, summary) domain-сервису.
 *
 * ## Архитектурные решения
 * - **Параметры задачи — через [TaskExecutionConfig.task3]** — CLI-слой
 *   обновляет [Task3Config] в [CliState.executionConfig], executor читает
 *   актуальные параметры из конфига при каждом вызове [execute].
 * - **Нет mutable state в executor'е** — все параметры приходят извне
 * - **Делегирует бизнес-логику** [PromptEngineeringService] — domain-сервис
 *   с полной поддержкой DIRECT/EXPERTS режимов и всех модификаторов
 * - **Flatten ExecuteResult → TaskResult** — [PromptEngineeringService.ExecuteResult]
 *   содержит несколько [TaskResult] (directResult, expertResponses, summary);
 *   executor преобразует их в единый [TaskResult.Success] с content и metadata
 * - **Не зависит от UI** — executor не содержит Terminal/Mordant, не обрабатывает CLI-команды
 *
 * @param promptEngineeringService domain-сервис промпт-инжиниринга
 */
class Task3Executor(
    private val promptEngineeringService: PromptEngineeringService
) : TaskExecutor {

    override val taskId: TaskId = TaskId(3)

    override val metadata: TaskMetadata = TaskMetadata(
        id = taskId,
        title = "Task 3: Промпт-инжиниринг с модульными модификаторами",
        description = "Промпт-инжиниринг: zero-shot, chain-of-thought, role-playing, multi-persona эксперты с synthesis.",
        availableCommands = listOf(":mode", ":step", ":role", ":experts", ":summary", ":config", ":reset")
    )

    /**
     * Выполняет Task3, читая параметры режима из [config.task3].
     *
     * Параметры (mode, step, role, experts, summary) больше не хранятся
     * в конструкторе executor'а — они передаются через [TaskExecutionConfig.task3],
     * что позволяет CLI-слою менять настройки без пересоздания executor'а.
     */
    override suspend fun execute(prompt: Prompt, config: TaskExecutionConfig): TaskResult {
        val task3 = config.task3
        return try {
            val mode = when (task3.mode) {
                Task3Mode.DIRECT -> PromptEngineeringService.Mode.DIRECT
                Task3Mode.EXPERTS -> PromptEngineeringService.Mode.EXPERTS
            }

            val result = promptEngineeringService.execute(
                prompt = prompt,
                mode = mode,
                step = task3.effectiveStepInstruction,
                meta = task3.metaEnabled,
                role = task3.effectiveRole,
                experts = task3.experts,
                summary = task3.isSummaryEnabled,
                config = config
            )

            flattenResult(result)
        } catch (e: Exception) {
            TaskResult.Error(
                message = "Task 3 execution failed: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Преобразует [PromptEngineeringService.ExecuteResult] в единый [TaskResult].
     */
    private fun flattenResult(result: PromptEngineeringService.ExecuteResult): TaskResult {
        return when (result.mode) {
            PromptEngineeringService.Mode.DIRECT -> {
                val directResult = result.directResult ?: TaskResult.Error("No direct result")
                if (directResult is TaskResult.Success && result.metaGeneratedPrompt != null) {
                    directResult.copy(
                        metadata = directResult.metadata + ("metaGeneratedPrompt" to result.metaGeneratedPrompt.value)
                    )
                } else {
                    directResult
                }
            }

            PromptEngineeringService.Mode.EXPERTS -> {
                val successResponses = result.expertResponses.filter {
                    it.result is TaskResult.Success
                }
                val failedResponses = result.expertResponses.filter {
                    it.result is TaskResult.Error
                }

                val content = buildString {
                    for (response in result.expertResponses) {
                        appendLine("### ${response.name}")
                        when (val r = response.result) {
                            is TaskResult.Success -> appendLine(r.content)
                            is TaskResult.Error -> appendLine("Error: ${r.message}")
                            is TaskResult.Partial -> appendLine(r.content)
                        }
                        appendLine()
                    }
                    val summaryContent = result.summary
                    if (summaryContent is TaskResult.Success) {
                        appendLine("### Итоговое заключение")
                        appendLine(summaryContent.content)
                    }
                }

                val metadata = buildMap<String, Any> {
                    put("mode", "EXPERTS")
                    put("expertCount", result.expertResponses.size)
                    put("successfulExperts", successResponses.size)
                    put("failedExperts", failedResponses.size)
                    put("hasSummary", result.summary != null)
                    put("expertNames", result.expertResponses.map { it.name })
                    result.metaGeneratedPrompt?.let {
                        put("metaGeneratedPrompt", it.value)
                    }
                }

                TaskResult.Success(content = content.trim(), metadata = metadata)
            }
        }
    }
}
