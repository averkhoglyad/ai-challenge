package io.averkhogliad.ai.challenge.week2.domain.telemetry

import io.averkhogliad.ai.challenge.week2.domain.telemetry.TokenTelemetry.Companion.aggregate


/**
 * Domain-модель агрегированной телеметрии диалога.
 *
 * Содержит информацию о токенах текущего шага, кумулятивную статистику
 * и оценку заполнения контекстного окна.
 *
 * ## Архитектурные решения
 * - **Immutable** — все поля val, неизменяемый объект-значение
 * - **Functional Core** — вычисляемые поля [isContextOverflow] и [contextUtilizationPercent]
 *     реализованы как extension-свойства без побочных эффектов
 * - **Companion factory** — метод [aggregate] инкапсулирует логику накопления статистики
 *
 * @property stepUsage использование токенов на текущем шаге
 * @property cumulativeUsage кумулятивное использование токенов за весь диалог
 * @property dialogHistoryTokens количество токенов в истории диалога (без текущего промпта)
 * @property contextWindowLimit лимит контекстного окна модели (если известен)
 * @property costEstimate оценка стоимости использования (опционально)
 */
data class TokenTelemetry(
    val stepUsage: TokenUsage,
    val cumulativeUsage: TokenUsage,
    val dialogHistoryTokens: Int,
    val contextWindowLimit: Int? = null,
    val costEstimate: CostEstimate? = null
) {
    init {
        require(dialogHistoryTokens >= 0) { "dialogHistoryTokens must be non-negative, got: $dialogHistoryTokens" }
    }

    /**
     * Признак переполнения контекстного окна.
     * Возвращает `true`, если кумулятивное использование токенов превышает лимит.
     */
    val isContextOverflow: Boolean
        get() = contextWindowLimit != null && cumulativeUsage.totalTokens > contextWindowLimit

    /**
     * Процент заполнения контекстного окна.
     * Возвращает `null`, если лимит контекстного окна неизвестен.
     */
    val contextUtilizationPercent: Double?
        get() = contextWindowLimit?.let { limit ->
            if (limit == 0) null
            else (cumulativeUsage.totalTokens.toDouble() / limit) * 100.0
        }

    companion object {
        /**
         * Агрегирует статистику: объединяет предыдущую телеметрию с текущим шагом.
         *
         * Если [previousTelemetry] == null, кумулятивное использование равно [currentStepUsage].
         *
         * @param previousTelemetry предыдущая агрегированная статистика (null для первого шага)
         * @param currentStepUsage использование токенов на текущем шаге
         * @param dialogHistoryTokens токены истории диалога
         * @param contextWindowLimit лимит контекстного окна
         * @param costEstimate оценка стоимости (опционально)
         * @return новый экземпляр [TokenTelemetry] с агрегированной статистикой
         */
        fun aggregate(
            previousTelemetry: TokenTelemetry?,
            currentStepUsage: TokenUsage,
            dialogHistoryTokens: Int,
            contextWindowLimit: Int? = null,
            costEstimate: CostEstimate? = null
        ): TokenTelemetry {
            val cumulativeUsage = if (previousTelemetry != null) {
                TokenUsage(
                    promptTokens = previousTelemetry.cumulativeUsage.promptTokens + currentStepUsage.promptTokens,
                    completionTokens = previousTelemetry.cumulativeUsage.completionTokens + currentStepUsage.completionTokens,
                    totalTokens = previousTelemetry.cumulativeUsage.totalTokens + currentStepUsage.totalTokens
                )
            } else {
                currentStepUsage
            }

            return TokenTelemetry(
                stepUsage = currentStepUsage,
                cumulativeUsage = cumulativeUsage,
                dialogHistoryTokens = dialogHistoryTokens,
                contextWindowLimit = contextWindowLimit,
                costEstimate = costEstimate
            )
        }

        /**
         * Создаёт начальную телеметрию для первого шага диалога.
         */
        fun initial(
            stepUsage: TokenUsage,
            dialogHistoryTokens: Int = 0,
            contextWindowLimit: Int? = null,
            costEstimate: CostEstimate? = null
        ): TokenTelemetry {
            return TokenTelemetry(
                stepUsage = stepUsage,
                cumulativeUsage = stepUsage,
                dialogHistoryTokens = dialogHistoryTokens,
                contextWindowLimit = contextWindowLimit,
                costEstimate = costEstimate
            )
        }
    }
}
