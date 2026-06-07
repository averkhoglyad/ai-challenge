package io.averkhogliad.ai.challenge.week0.task5

import io.averkhogliad.ai.challenge.utils.llm.ChatResponse
import io.averkhogliad.ai.challenge.utils.llm.ModelInfo

/**
 * Результат бенчмарка одной модели.
 *
 * @property modelInfo Информация о модели
 * @property response Ответ от API (null если произошла ошибка)
 * @property responseTimeMs Время ответа в миллисекундах
 * @property estimatedCost Рассчитанная стоимость запроса (null если тариф не указан)
 * @property error Сообщение об ошибке, если запрос завершился неудачно
 */
data class ModelBenchmarkResult(
    val modelInfo: ModelInfo,
    val response: ChatResponse?,
    val responseTimeMs: Long,
    val estimatedCost: Double?,
    val error: String? = null
)
