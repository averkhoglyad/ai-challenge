package io.averkhogliad.ai.challenge.week4.cli.domain.rag.model

/**
 * Результат проверки порога релевантности.
 *
 * Определяет, достаточно ли контекст релевантен для генерации ответа
 * или следует вернуть режим «не знаю».
 */
sealed interface RelevanceCheckResult {

    /**
     * Контекст достаточно релевантен — можно генерировать ответ с цитатами.
     *
     * @property chunks отфильтрованные релевантные чанки
     * @property maxScore максимальный показатель релевантности среди чанков
     * @property averageScore средний показатель релевантности
     */
    data class Sufficient(
        val chunks: List<RelevantChunk>,
        val maxScore: Float,
        val averageScore: Float
    ) : RelevanceCheckResult

    /**
     * Контекст недостаточно релевантен — возвращаем «не знаю».
     *
     * @property maxScore максимальный показатель релевантности среди чанков
     * @property threshold порог, который не был достигнут
     * @property chunks все найденные чанки (для диагностики)
     */
    data class Insufficient(
        val maxScore: Float,
        val threshold: Float,
        val chunks: List<RelevantChunk>
    ) : RelevanceCheckResult
}
