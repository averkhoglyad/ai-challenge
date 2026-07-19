package io.averkhogliad.ai.challenge.week0.domain.service

import io.averkhogliad.ai.challenge.week0.domain.ModelId
import io.averkhogliad.ai.challenge.week0.domain.TaskResult
import io.averkhogliad.ai.challenge.week0.domain.config.BenchmarkConfig
import io.averkhogliad.ai.challenge.week0.domain.config.TaskExecutionConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Domain-сервис для сравнения производительности LLM-моделей.
 *
 * - Параллельное выполнение одного промпта на нескольких моделях
 * - Замер времени ответа каждой модели
 * - Сравнение результатов
 *
 * Зависит только от [LlmPort] (port) и domain-моделей ([ModelId], [TaskResult],
 * [BenchmarkConfig], [TaskExecutionConfig]). Не зависит от конкретных LLM-клиентов
 * и [io.averkhogliad.ai.challenge.llm.chat.ModelInfo].
 *
 * ## Как работает
 * 1. Для каждой модели из [BenchmarkConfig.modelIds] создаётся [TaskExecutionConfig] с указанным modelId
 * 2. Все запросы выполняются параллельно через [coroutineScope]
 * 3. Для каждого запроса замеряется длительность
 * 4. Результаты собираются в список [ModelBenchmarkResult]
 */
class ModelBenchmarkService(
    private val llmPort: LlmPort
) {
    /**
     * Результат бенчмарка одной модели.
     *
     * @property modelId идентификатор модели
     * @property result результат выполнения запроса
     * @property durationMs длительность запроса в миллисекундах
     */
    data class ModelBenchmarkResult(
        val modelId: ModelId,
        val result: TaskResult,
        val durationMs: Long
    )

    /**
     * Сводная статистика бенчмарка.
     *
     * @property totalModels общее количество протестированных моделей
     * @property successfulModels количество успешных запросов
     * @property failedModels количество запросов с ошибкой
     * @property fastestModel самая быстрая модель (modelId и durationMs)
     * @property slowestModel самая медленная модель (modelId и durationMs)
     * @property avgDurationMs среднее время ответа по успешным запросам
     */
    data class BenchmarkSummary(
        val totalModels: Int,
        val successfulModels: Int,
        val failedModels: Int,
        val fastestModel: Pair<ModelId, Long>?,
        val slowestModel: Pair<ModelId, Long>?,
        val avgDurationMs: Long
    )

    companion object {
        /** Значение maxTokens по умолчанию. */
        private const val DEFAULT_MAX_TOKENS = 500
    }

    /**
     * Выполняет бенчмарк моделей в соответствии с [BenchmarkConfig].
     *
     * Все запросы выполняются параллельно.
     *
     * @param config конфигурация бенчмарка: модели, температуры, промпт, maxTokens
     * @return список [ModelBenchmarkResult] — по одному результату на каждую модель
     */
    suspend fun benchmarkModels(
        config: BenchmarkConfig
    ): List<ModelBenchmarkResult> {
        return coroutineScope {
            config.modelIds.map { modelId ->
                async {
                    benchmarkSingleModel(modelId, config)
                }
            }.awaitAll()
        }
    }

    /**
     * Бенчмарк одной модели: замеряет время выполнения запроса.
     *
     * @param modelId идентификатор модели
     * @param config конфигурация бенчмарка
     * @return [ModelBenchmarkResult] с результатом и длительностью
     */
    private suspend fun benchmarkSingleModel(
        modelId: ModelId,
        config: BenchmarkConfig
    ): ModelBenchmarkResult {
        val executionConfig = TaskExecutionConfig(
            maxTokens = config.maxTokens,
            modelId = modelId
        )

        val startTime = System.currentTimeMillis()
        val result = llmPort.chat(config.prompt, executionConfig)
        val durationMs = System.currentTimeMillis() - startTime

        return ModelBenchmarkResult(
            modelId = modelId,
            result = result,
            durationMs = durationMs
        )
    }

    /**
     * Вычисляет сводную статистику по результатам бенчмарка.
     *
     * Чистая функция: не имеет побочных эффектов.
     *
     * @param results список результатов бенчмарка
     * @return [BenchmarkSummary] с агрегированной статистикой
     */
    fun computeSummary(results: List<ModelBenchmarkResult>): BenchmarkSummary {
        val successful = results.filter { it.result is TaskResult.Success }
        val failed = results.filter { it.result is TaskResult.Error }

        val fastest = successful.minByOrNull { it.durationMs }
        val slowest = successful.maxByOrNull { it.durationMs }
        val avgDuration = if (successful.isNotEmpty()) {
            successful.sumOf { it.durationMs } / successful.size
        } else {
            0L
        }

        return BenchmarkSummary(
            totalModels = results.size,
            successfulModels = successful.size,
            failedModels = failed.size,
            fastestModel = fastest?.let { it.modelId to it.durationMs },
            slowestModel = slowest?.let { it.modelId to it.durationMs },
            avgDurationMs = avgDuration
        )
    }
}
