package io.averkhogliad.ai.challenge.week3.cli.domain.telemetry

import io.averkhogliad.ai.challenge.week3.cli.domain.telemetry.CostEstimate.Companion.calculate


/**
 * Domain-модель оценки стоимости использования LLM.
 *
 * Рассчитывает стоимость на основе потребленных токенов и цен за токен.
 * Цена указывается в рублях за 1 миллион токенов, стоимость рассчитывается
 * напрямую без дополнительной конвертации валют.
 *
 * ## Архитектурные решения
 * - **Immutable** — все поля val, неизменяемый объект-значение
 * - **Companion factory** — метод [calculate] инкапсулирует логику расчета
 *
 * @property inputCostPerToken стоимость одного входного токена (prompt) в рублях
 * @property outputCostPerToken стоимость одного выходного токена (completion) в рублях
 * @property inputCost общая стоимость входных токенов в рублях
 * @property outputCost общая стоимость выходных токенов в рублях
 * @property totalCost общая стоимость в рублях
 */
data class CostEstimate(
    val inputCostPerToken: Double,
    val outputCostPerToken: Double,
    val inputCost: Double,
    val outputCost: Double,
    val totalCost: Double
) {
    init {
        require(inputCostPerToken >= 0.0) {
            "inputCostPerToken must be non-negative, got: $inputCostPerToken"
        }
        require(outputCostPerToken >= 0.0) {
            "outputCostPerToken must be non-negative, got: $outputCostPerToken"
        }
        require(inputCost >= 0.0) { "inputCost must be non-negative, got: $inputCost" }
        require(outputCost >= 0.0) { "outputCost must be non-negative, got: $outputCost" }
        require(totalCost >= 0.0) { "totalCost must be non-negative, got: $totalCost" }
    }

    companion object {
        /**
         * Рассчитывает стоимость на основе использования токенов и цен.
         *
         * @param usage использование токенов (prompt + completion)
         * @param inputCostPerToken стоимость одного входного токена в рублях
         * @param outputCostPerToken стоимость одного выходного токена в рублях
         * @return экземпляр [CostEstimate] с рассчитанными стоимостями
         */
        fun calculate(
            usage: TokenUsage,
            inputCostPerToken: Double,
            outputCostPerToken: Double
        ): CostEstimate {
            require(inputCostPerToken >= 0.0) {
                "inputCostPerToken must be non-negative, got: $inputCostPerToken"
            }
            require(outputCostPerToken >= 0.0) {
                "outputCostPerToken must be non-negative, got: $outputCostPerToken"
            }

            val inputCost = usage.promptTokens * inputCostPerToken
            val outputCost = usage.completionTokens * outputCostPerToken

            return CostEstimate(
                inputCostPerToken = inputCostPerToken,
                outputCostPerToken = outputCostPerToken,
                inputCost = inputCost,
                outputCost = outputCost,
                totalCost = inputCost + outputCost
            )
        }

        /**
         * Рассчитывает кумулятивную стоимость на основе всей истории использования токенов.
         *
         * @param usages список [TokenUsage] за все шаги диалога
         * @param inputCostPerToken стоимость одного входного токена в рублях
         * @param outputCostPerToken стоимость одного выходного токена в рублях
         * @return экземпляр [CostEstimate] с агрегированной стоимостью
         */
        fun calculateCumulative(
            usages: List<TokenUsage>,
            inputCostPerToken: Double,
            outputCostPerToken: Double
        ): CostEstimate {
            val totalPrompt = usages.sumOf { it.promptTokens }
            val totalCompletion = usages.sumOf { it.completionTokens }
            val totalUsage = TokenUsage(totalPrompt, totalCompletion, totalPrompt + totalCompletion)
            return calculate(totalUsage, inputCostPerToken, outputCostPerToken)
        }
    }
}
