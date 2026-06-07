package io.averkhogliad.ai.challenge.utils.llm

import io.averkhogliad.ai.challenge.utils.config.Config
import java.util.*

/**
 * Информация о модели LLM, включая идентификатор, имя и стоимость.
 *
 * Формат записи в конфигурации: `{id}[:{name}][({costIn},{costOut})]`
 * Стоимость указывается в ₽ за 1 000 000 (1M) токенов.
 *
 * Примеры:
 * - `minimax/minimax-m3` — только ID
 * - `minimax/minimax-m3:Minimax M3` — ID + имя
 * - `minimax/minimax-m3(100)` — ID + одинаковая стоимость за 1M токенов
 * - `openai/gpt-4o(238,955)` — ID + разная стоимость input/output за 1M токенов
 * - `minimax/minimax-m3:Minimax M3(28,114)` — полный формат
 *
 * @property modelId Идентификатор модели для API (например, "minimax/minimax-m3")
 * @property name Человекочитаемое имя модели (по умолчанию совпадает с modelId)
 * @property costPerMillionInputTokens Стоимость за 1 000 000 токенов входных данных (₽), null если не указана
 * @property costPerMillionOutputTokens Стоимость за 1 000 000 токенов выходных данных (₽), null если не указана
 */
data class ModelInfo(
    val modelId: String,
    val name: String = modelId,
    val costPerMillionInputTokens: Double? = null,
    val costPerMillionOutputTokens: Double? = null
) {
    /**
     * Рассчитывает стоимость запроса на основе количества токенов.
     *
     * @param promptTokens Количество токенов в запросе
     * @param completionTokens Количество токенов в ответе
     * @return Стоимость в ₽, или null если тариф не указан
     */
    fun calculateCost(promptTokens: Int, completionTokens: Int): Double? {
        val inputCost = costPerMillionInputTokens ?: return null
        val outputCost = costPerMillionOutputTokens ?: inputCost
        return (promptTokens / 1_000_000.0) * inputCost + (completionTokens / 1_000_000.0) * outputCost
    }

    /**
     * Форматирует информацию о тарифе для отображения.
     *
     * @return Строка с описанием тарифа, например "₽0.10/1M токенов" или "₽2.50/1M input, ₽10.00/1M output"
     */
    fun formatTariff(): String {
        val inputCost = costPerMillionInputTokens ?: return "бесплатно"
        val outputCost = costPerMillionOutputTokens
        return if (outputCost == null || outputCost == inputCost) {
            "₽${String.format(Locale.US, "%.2f", inputCost)}/1M токенов"
        } else {
            "₽${String.format(Locale.US, "%.2f", inputCost)}/1M input, ₽${
                String.format(
                    Locale.US,
                    "%.2f",
                    outputCost
                )
            }/1M output"
        }
    }

    companion object {
        /**
         * Парсит одну запись модели из строки формата `{id}[:{name}][({costIn},{costOut})]`.
         *
         * @param entry Строка с описанием модели
         * @return [ModelInfo] с распарсенными данными
         * @throws IllegalArgumentException если формат некорректен
         */
        fun parse(entry: String): ModelInfo {
            require(entry.isNotBlank()) { "Model entry cannot be blank" }

            val trimmed = entry.trim()

            // Извлекаем cost block (в скобках)
            val costBlockStart = trimmed.lastIndexOf('(')
            val costBlockEnd = trimmed.lastIndexOf(')')

            val costPart: String?
            val beforeCost: String

            if (costBlockStart != -1 && costBlockEnd != -1 && costBlockEnd > costBlockStart) {
                costPart = trimmed.substring(costBlockStart + 1, costBlockEnd)
                beforeCost = trimmed.substring(0, costBlockStart).trim()
            } else {
                costPart = null
                beforeCost = trimmed
            }

            // Парсим cost
            var costIn: Double? = null
            var costOut: Double? = null

            if (costPart != null) {
                val costParts = costPart.split(",").map { it.trim() }
                require(costParts.size <= 2) {
                    "Cost block must have at most 2 values (input,output) in entry: $entry, got ${costParts.size}"
                }

                costIn = costParts[0].toDoubleOrNull()
                    ?: throw IllegalArgumentException("Invalid input cost format '${costParts[0]}' in entry: $entry")
                require(costIn >= 0) { "Input cost must be non-negative in entry: $entry" }

                if (costParts.size == 2) {
                    costOut = costParts[1].toDoubleOrNull()
                        ?: throw IllegalArgumentException("Invalid output cost format '${costParts[1]}' in entry: $entry")
                    require(costOut >= 0) { "Output cost must be non-negative in entry: $entry" }
                }
            }

            // Парсим id и name (разделитель ':')
            val colonIndex = beforeCost.indexOf(':')
            val modelId: String
            val name: String

            if (colonIndex != -1) {
                modelId = beforeCost.substring(0, colonIndex).trim()
                name = beforeCost.substring(colonIndex + 1).trim()
            } else {
                modelId = beforeCost.trim()
                name = modelId
            }

            require(modelId.isNotBlank()) { "Model ID cannot be blank in entry: $entry" }

            return ModelInfo(
                modelId = modelId,
                name = name,
                costPerMillionInputTokens = costIn,
                costPerMillionOutputTokens = costOut
            )
        }

        /**
         * Парсит список моделей из строки, разделённой запятыми.
         *
         * Формат: `model1,model2,model3`
         *
         * @param value Строка со списком моделей
         * @return Список [ModelInfo]
         */
        fun parseList(value: String): List<ModelInfo> {
            if (value.isBlank()) return emptyList()

            // Разделяем по запятым, но учитываем, что внутри скобок тоже могут быть запятые
            val entries = mutableListOf<String>()
            var current = StringBuilder()
            var parenDepth = 0

            for (char in value) {
                when {
                    char == '(' -> {
                        parenDepth++
                        current.append(char)
                    }
                    char == ')' -> {
                        parenDepth--
                        current.append(char)
                    }
                    char == ',' && parenDepth == 0 -> {
                        val entry = current.toString().trim()
                        if (entry.isNotBlank()) {
                            entries.add(entry)
                        }
                        current = StringBuilder()
                    }
                    else -> current.append(char)
                }
            }

            val lastEntry = current.toString().trim()
            if (lastEntry.isNotBlank()) {
                entries.add(lastEntry)
            }

            return entries.map { parse(it) }
        }
    }
}

/**
 * Extension-функция для парсинга строки в [ModelInfo].
 *
 * Пример использования:
 * ```kotlin
 * val model = "minimax/minimax-m3:Minimax M3(28)".toModelInfo()
 * ```
 *
 * @return [ModelInfo] с распарсенными данными
 * @throws IllegalArgumentException если формат некорректен
 */
fun String.toModelInfo(): ModelInfo = ModelInfo.parse(this)

/**
 * Extension-функция для парсинга строки со списком моделей в [List] of [ModelInfo].
 *
 * Разделяет строку по запятым (учитывая скобки) и парсит каждую запись.
 *
 * Пример использования:
 * ```kotlin
 * val models = "model1,model2(1.0),model3:Name(2.0,10.0)".toModelInfoList()
 * ```
 *
 * @return Список [ModelInfo]
 */
fun String.toModelInfoList(): List<ModelInfo> = ModelInfo.parseList(this)

/**
 * Extension-функция для загрузки списка моделей из конфигурации.
 *
 * Получает значение по ключу [key] и парсит его как список моделей.
 * Если ключ отсутствует или значение пустое, возвращает пустой список.
 *
 * Пример использования:
 * ```kotlin
 * val models = config.loadModels()  // использует ключ "models" по умолчанию
 * val customModels = config.loadModels("custom.models")
 * ```
 *
 * @param key Ключ в конфигурации (по умолчанию "models")
 * @return Список [ModelInfo]
 */
fun Config.loadModels(key: String = "models"): List<ModelInfo> {
    return getOrNull(key)?.takeIf { it.isNotBlank() }?.toModelInfoList() ?: emptyList()
}
