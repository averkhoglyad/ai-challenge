package io.averkhogliad.ai.challenge.week0.domain.service

import io.averkhogliad.ai.challenge.week0.domain.Prompt
import io.averkhogliad.ai.challenge.week0.domain.TaskResult
import io.averkhogliad.ai.challenge.week0.domain.config.TaskExecutionConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Domain-сервис для демонстрации влияния temperature на генерацию.
 *
 * - Параллельное выполнение одного промпта с разными значениями temperature
 * - Сравнение результатов: длина, разнообразие, детерминированность
 *
 * Зависит только от [LlmPort] (port) и domain-моделей ([Prompt], [TaskResult], [TaskExecutionConfig]).
 * Не зависит от UI (terminal) и конкретных LLM-клиентов.
 *
 * ## Как работает
 * 1. Для каждого значения temperature создаётся отдельный запрос с копией конфига
 * 2. Все запросы выполняются параллельно через [coroutineScope]
 * 3. Результаты собираются в список [TemperatureResult]
 */
class TemperatureService(
    private val llmPort: LlmPort
) {
    /**
     * Result of benchmarking a single temperature value.
     */
    data class TemperatureResult(
        val temperature: Double,
        val result: TaskResult
    )

    companion object {
        /** Значения temperature по умолчанию. */
        private val DEFAULT_TEMPERATURES = listOf(0.0, 0.7, 1.2)

        /** Классификация значений temperature для интерпретации результатов. */
        fun describeTemperature(temp: Double): String = when {
            temp == 0.0 -> "максимальная детерминированность"
            temp < 0.3 -> "высокая детерминированность"
            temp < 0.7 -> "умеренная случайность"
            temp < 1.0 -> "сбалансированный режим"
            temp < 1.5 -> "повышенная креативность"
            else -> "максимальная креативность"
        }
    }

    /**
     * Выполняет бенчмарк заданных значений temperature.
     *
     * Все запросы выполняются параллельно.
     *
     * @param prompt промпт пользователя
     * @param temperatures список значений temperature для тестирования
     * @param config базовая конфигурация выполнения (temperature переопределяется для каждого запроса)
     * @return список [TemperatureResult] в порядке указанных temperature
     */
    suspend fun benchmarkTemperatures(
        prompt: Prompt,
        temperatures: List<Double> = DEFAULT_TEMPERATURES,
        config: TaskExecutionConfig
    ): List<TemperatureResult> {
        require(temperatures.isNotEmpty()) { "temperatures cannot be empty" }
        require(temperatures.all { it in 0.0..2.0 }) {
            "all temperatures must be in 0.0..2.0"
        }

        return coroutineScope {
            temperatures.map { temp ->
                async {
                    val tempConfig = config.copy(temperature = temp)
                    val result = llmPort.chat(prompt, tempConfig)
                    TemperatureResult(temp, result)
                }
            }.awaitAll()
        }
    }

    /**
     * Вычисляет статистику по результатам бенчмарка.
     *
     * Чистая функция: не имеет побочных эффектов.
     *
     * @param results список результатов бенчмарка
     * @return карта с ключами статистики:
     *   - "successfulCount" — количество успешных запросов
     *   - "errorCount" — количество ошибок
     *   - "totalContentLength" — суммарная длина контента
     *   - "avgContentLength" — средняя длина контента
     *   - "contentLengthByTemperature" — карта temperature -> длина контента
     */
    fun computeStatistics(results: List<TemperatureResult>): Map<String, Any> {
        val successful = results.filter { it.result is TaskResult.Success }
        val failed = results.filter { it.result is TaskResult.Error }

        val contentLengthByTemperature = mutableMapOf<Double, Int>()
        var totalContentLength = 0

        for (r in successful) {
            val content = (r.result as TaskResult.Success).content
            contentLengthByTemperature[r.temperature] = content.length
            totalContentLength += content.length
        }

        return mapOf(
            "successfulCount" to successful.size,
            "errorCount" to failed.size,
            "totalContentLength" to totalContentLength,
            "avgContentLength" to if (successful.isNotEmpty()) totalContentLength / successful.size else 0,
            "contentLengthByTemperature" to contentLengthByTemperature
        )
    }

}
