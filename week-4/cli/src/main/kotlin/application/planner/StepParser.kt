package io.averkhogliad.ai.challenge.week4.cli.application.planner

/**
 * Парсит ответ LLM в список шагов планирования.
 *
 * ## Архитектурная роль
 * - **Application Layer** — специализированный компонент-стратегия
 * - **Single Responsibility** — только парсинг текстового ответа в шаги
 * - **Stateless** — не хранит состояние, полностью testable
 *
 * ## Поддерживаемые форматы
 * - Нумерованный список: `1. Шаг`, `1) Шаг`
 * - Маркированный список: `- Шаг`, `* Шаг`
 *
 * ## Использование
 * ```kotlin
 * val parser = StepParser()
 * val steps = parser.parse("1. Установить зависимости\n2. Настроить конфигурацию")
 * // -> ["Установить зависимости", "Настроить конфигурацию"]
 * ```
 */
class StepParser {

    /**
     * Парсит текстовый ответ LLM в список шагов.
     *
     * Алгоритм:
     * 1. Разбивает ответ на строки
     * 2. Для каждой строки ищет нумерацию (1., 2) и т.д.) или маркер (-, *)
     * 3. Извлекает текст шага, отбрасывая пустые строки
     *
     * @param response текстовый ответ от LLM
     * @return список строк-шагов (может быть пустым, если шаги не найдены)
     */
    fun parse(response: String): List<String> {
        val steps = mutableListOf<String>()
        val lines = response.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // Нумерованный список: 1. Шаг или 1) Шаг
            val numberedMatch = Regex("""^\d+[\.\)]\s*(.+)""").find(trimmed)
            if (numberedMatch != null) {
                val stepText = numberedMatch.groupValues[1].trim()
                if (stepText.isNotEmpty()) {
                    steps.add(stepText)
                }
            } else if (trimmed.startsWith("-") || trimmed.startsWith("*")) {
                // Маркированный список: - Шаг или * Шаг
                val stepText = trimmed.removePrefix("-").removePrefix("*").trim()
                if (stepText.isNotEmpty()) {
                    steps.add(stepText)
                }
            }
        }

        return steps
    }
}
