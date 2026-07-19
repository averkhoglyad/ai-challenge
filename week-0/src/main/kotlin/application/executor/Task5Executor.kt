package io.averkhogliad.ai.challenge.week0.application.executor

import io.averkhogliad.ai.challenge.week0.domain.*
import io.averkhogliad.ai.challenge.week0.domain.config.BenchmarkConfig
import io.averkhogliad.ai.challenge.week0.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week0.domain.service.ModelBenchmarkService

/**
 * Executor для Task 5: сравнение производительности LLM моделей.
 *
 * Оркестрирует вызов [ModelBenchmarkService.benchmarkModels] — выполняет
 * один и тот же промпт на нескольких моделях и агрегирует результаты
 * с информацией о времени ответа.
 *
 * ## Архитектурные решения
 * - **Параметры бенчмарка — через конструктор** — modelIds задаются
 *   при создании executor'а. UI/CLI управляет выбором моделей.
 * - **Преобразование конфига** — executor строит [BenchmarkConfig]
 *   из [TaskExecutionConfig] и списка modelIds
 * - **Делегирует бизнес-логику** [ModelBenchmarkService] — domain-сервис
 *   с параллельным выполнением запросов и вычислением сводной статистики
 * - **Агрегирует результаты** — список [ModelBenchmarkService.ModelBenchmarkResult]
 *   преобразуется в единый [TaskResult.Success] с полным контентом
 *   и статистикой в metadata
 * - **Не зависит от UI** — executor не содержит Terminal/Mordant, не зависит от [ModelInfo][io.averkhogliad.ai.challenge.llm.chat.ModelInfo]
 *
 * Executor содержит только оркестрацию: построение конфига → вызов сервиса → агрегация.
 */
class Task5Executor(
    private val modelBenchmarkService: ModelBenchmarkService,
    private val defaultModelIds: List<ModelId> = emptyList(),
) : TaskExecutor {

    override val taskId: TaskId = TaskId(5)

    override val metadata: TaskMetadata = TaskMetadata(
        id = taskId,
        title = "Task 5: Сравнение производительности моделей",
        description = "Выполняет один запрос на нескольких моделях и сравнивает время ответа и результаты.",
        availableCommands = listOf(":models", ":maxTokens", ":reset", ":params")
    )

    override suspend fun execute(prompt: Prompt, config: TaskExecutionConfig): TaskResult {
        return try {
            // Если в конфиге заданы модели (через CLI :models или Task5Config), используем их; иначе — из конструктора
            val effectiveModelIds = if (config.task5.isNotEmpty) config.task5.modelIds else defaultModelIds
            val benchmarkConfig = BenchmarkConfig(
                modelIds = effectiveModelIds,
                maxTokens = config.maxTokens,
                prompt = prompt
            )

            val results = modelBenchmarkService.benchmarkModels(benchmarkConfig)
            val summary = modelBenchmarkService.computeSummary(results)

            flattenResults(results, summary)
        } catch (e: Exception) {
            TaskResult.Error(
                message = "Task 5 execution failed: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Преобразует результаты бенчмарка и сводную статистику в единый [TaskResult].
     */
    private fun flattenResults(
        results: List<ModelBenchmarkService.ModelBenchmarkResult>,
        summary: ModelBenchmarkService.BenchmarkSummary
    ): TaskResult {
        val content = buildString {
            for (r in results) {
                appendLine("### ${r.modelId.value}")
                appendLine("- Duration: ${r.durationMs}ms")
                when (val res = r.result) {
                    is TaskResult.Success -> {
                        appendLine("- Response:")
                        appendLine(res.content)
                    }

                    is TaskResult.Error -> appendLine("- Error: ${res.message}")
                    is TaskResult.Partial -> appendLine("- Partial: ${res.content}")
                }
                appendLine()
            }

            appendLine("---")
            appendLine("### Benchmark Summary")
            appendLine("- Total models: ${summary.totalModels}")
            appendLine("- Successful: ${summary.successfulModels}")
            appendLine("- Failed: ${summary.failedModels}")
            summary.fastestModel?.let {
                appendLine("- Fastest: ${it.first.value} (${it.second}ms)")
            }
            summary.slowestModel?.let {
                appendLine("- Slowest: ${it.first.value} (${it.second}ms)")
            }
            appendLine("- Avg duration: ${summary.avgDurationMs}ms")
        }

        val metadata = buildMap<String, Any> {
            put("totalModels", summary.totalModels)
            put("successfulModels", summary.successfulModels)
            put("failedModels", summary.failedModels)
            put("avgDurationMs", summary.avgDurationMs)
            summary.fastestModel?.let {
                put("fastestModel", it.first.value)
                put("fastestDurationMs", it.second)
            }
            summary.slowestModel?.let {
                put("slowestModel", it.first.value)
                put("slowestDurationMs", it.second)
            }
        }

        return TaskResult.Success(
            content = content.trim(),
            metadata = metadata
        )
    }
}
