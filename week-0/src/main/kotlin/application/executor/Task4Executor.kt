package io.averkhogliad.ai.challenge.week0.application.executor

import io.averkhogliad.ai.challenge.week0.domain.Prompt
import io.averkhogliad.ai.challenge.week0.domain.TaskId
import io.averkhogliad.ai.challenge.week0.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week0.domain.TaskResult
import io.averkhogliad.ai.challenge.week0.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week0.domain.service.TemperatureService

/**
 * Executor для Task 4: демонстрация влияния temperature на генерацию.
 *
 * Оркестрирует вызов [TemperatureService.benchmarkTemperatures] — выполняет
 * один и тот же промпт с разными значениями temperature и агрегирует
 * результаты.
 *
 * ## Архитектурные решения
 * - **Параметры бенчмарка — через конструктор** — temperatures задаются
 *   при создании executor'а. UI/CLI управляет выбором значений.
 * - **Делегирует бизнес-логику** [TemperatureService] — domain-сервис
 *   с параллельным выполнением запросов и вычислением статистики
 * - **Агрегирует результаты** — список [TemperatureService.TemperatureResult]
 *   преобразуется в единый [TaskResult.Success] с полным контентом
 *   и статистикой в metadata
 * - **Не зависит от UI** — executor не содержит Terminal/Mordant, не выводит прогресс
 *
 * Executor содержит только оркестрацию: вызов сервиса → агрегация результата.
 */
class Task4Executor(
    private val temperatureService: TemperatureService
) : TaskExecutor {

    override val taskId: TaskId = TaskId(4)

    override val metadata: TaskMetadata = TaskMetadata(
        id = taskId,
        title = "Task 4: Влияние temperature на генерацию",
        description = "Выполняет один запрос с разными значениями temperature и сравнивает результаты.",
        availableCommands = listOf(":temp", ":maxTokens", ":reset", ":params")
    )

    override suspend fun execute(prompt: Prompt, config: TaskExecutionConfig): TaskResult {
        return try {
            val results = temperatureService.benchmarkTemperatures(
                prompt = prompt,
                temperatures = config.task4.temperatures,
                config = config
            )

            flattenResults(results)
        } catch (e: Exception) {
            TaskResult.Error(
                message = "Task 4 execution failed: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Преобразует список [TemperatureService.TemperatureResult] в единый [TaskResult].
     *
     * Агрегирует контент и статистику в [TaskResult.Success].
     */
    private fun flattenResults(results: List<TemperatureService.TemperatureResult>): TaskResult {
        val successful = results.filter { it.result is TaskResult.Success }
        val failed = results.filter { it.result is TaskResult.Error }

        val content = buildString {
            for (r in results) {
                val description = TemperatureService.describeTemperature(r.temperature)
                appendLine("### Temperature: ${r.temperature} ($description)")
                when (val res = r.result) {
                    is TaskResult.Success -> appendLine(res.content)
                    is TaskResult.Error -> appendLine("Error: ${res.message}")
                    is TaskResult.Partial -> appendLine(res.content)
                }
                appendLine()
            }
        }

        val statistics = temperatureService.computeStatistics(results)

        val metadata = buildMap<String, Any> {
            putAll(statistics)
            put("temperatures", results.map { it.temperature })
            put("successfulCount", successful.size)
            put("errorCount", failed.size)
        }

        return TaskResult.Success(
            content = content.trim(),
            metadata = metadata
        )
    }
}
