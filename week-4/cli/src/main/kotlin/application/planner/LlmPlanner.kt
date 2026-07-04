package io.averkhogliad.ai.challenge.week4.cli.application.planner

import io.averkhogliad.ai.challenge.week4.cli.domain.Prompt
import io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week4.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.Invariant
import io.averkhogliad.ai.challenge.week4.cli.domain.service.LlmPort

/**
 * Взаимодействие с LLM для генерации плана шагов задачи.
 *
 * ## Архитектурная роль
 * - **Application Layer** — специализированный компонент-стратегия
 * - **Single Responsibility** — построение промпта и вызов LLM для планирования
 * - **Testable** — зависит только от интерфейса [LlmPort]
 *
 * ## Ответственности
 * - Построение промпта с учётом инвариантов, контекста и фактов
 * - Вызов LLM через [LlmPort.chat]
 * - Распознавание отказа LLM из-за конфликта с инвариантами
 * - Извлечение текста конфликтующего инварианта из ответа
 *
 * ## Использование
 * ```kotlin
 * val planner = LlmPlanner(llmPort)
 * val result = planner.plan("Добавить кэширование", "Нужен Redis", "• Факт 1", "[INVARIANTS] ...")
 * ```
 *
 * @property llmPort порт для взаимодействия с LLM
 * @property temperature температура генерации (по умолчанию 0.7)
 * @property maxTokens максимальное количество токенов в ответе (по умолчанию 2000)
 */
class LlmPlanner(
    private val llmPort: LlmPort,
    private val temperature: Double = 0.7,
    private val maxTokens: Int = 2000
) {

    /**
     * Результат вызова LLM для планирования.
     */
    sealed class PlanResult {
        /** Успешный ответ LLM (текст ответа). */
        data class Success(val response: String) : PlanResult()

        /** LLM отказалась генерировать план из-за конфликта с инвариантами. */
        data class InvariantRefusal(val conflictingRule: String) : PlanResult()

        /** Ошибка взаимодействия с LLM. */
        data class Error(val message: String) : PlanResult()
    }

    /**
     * Выполняет планирование: строит промпт и отправляет запрос к LLM.
     *
     * @param taskTitle название задачи
     * @param description описание задачи
     * @param relevantFacts текстовое представление релевантных фактов
     * @param invariantsText текстовый блок инвариантов (может быть пустым)
     * @param invariantsPresent флаг наличия инвариантов (для проверки отказа)
     * @return результат планирования
     */
    suspend fun plan(
        taskTitle: String,
        description: String,
        relevantFacts: String,
        invariantsText: String = "",
        invariantsPresent: Boolean = false
    ): PlanResult {
        val prompt = buildPrompt(taskTitle, description, relevantFacts, invariantsText)
        val config = TaskExecutionConfig(
            temperature = temperature,
            maxTokens = maxTokens
        )

        val result = try {
            llmPort.chat(Prompt(prompt), config)
        } catch (e: Exception) {
            return PlanResult.Error(e.message ?: "Неизвестная ошибка")
        }

        return when (result) {
            is TaskResult.Success -> {
                if (invariantsPresent && isInvariantRefusal(result.content)) {
                    val conflictingRule = extractConflictingRule(result.content)
                    PlanResult.InvariantRefusal(conflictingRule)
                } else {
                    PlanResult.Success(result.content)
                }
            }

            is TaskResult.Error -> PlanResult.Error(result.message)
            is TaskResult.Partial -> PlanResult.Error("Получен частичный результат от LLM")
        }
    }

    /**
     * Формирует промпт для LLM для генерации шагов планирования.
     */
    fun buildPrompt(
        taskTitle: String,
        description: String,
        relevantFacts: String,
        invariantsText: String = ""
    ): String {
        return buildString {
            // Блок инвариантов — всегда первый, выше всех инструкций
            if (invariantsText.isNotEmpty()) {
                appendLine(invariantsText)
                appendLine()
            }

            appendLine("Ты — помощник по планированию задач. Твоя задача — разбить задачу на конкретные выполнимые шаги.")
            appendLine()

            // Правила обработки инвариантов при планировании
            if (invariantsText.isNotEmpty()) {
                appendLine("=== ПРАВИЛА ОБРАБОТКИ ИНВАРИАНТОВ ПРИ ПЛАНИРОВАНИИ ===")
                appendLine("ЖЁСТКИЕ ПРАВИЛА (ИНВАРИАНТЫ) — это ограничения, которые ты НЕ ИМЕЕШЬ ПРАВА НАРУШАТЬ ни при каких обстоятельствах.")
                appendLine()
                appendLine("Перед генерацией шагов ВСЕГДА проверяй:")
                appendLine("1. Противоречит ли САМА ЗАДАЧА какому-либо инварианту?")
                appendLine("2. Если задача САМА ПО СЕБЕ нарушает инвариант (например, «Миграция на MongoDB» при инварианте «Только PostgreSQL»):")
                appendLine("   — НЕ генерируй шаги для этой задачи")
                appendLine("   — Вместо списка шагов напиши ТОЛЬКО сообщение об отказе в формате:")
                appendLine("     ❌ Нарушение инварианта: [укажи нарушенный инвариант]")
                appendLine("     Задача «[название]» противоречит инварианту: [процитируй правило].")
                appendLine("     💡 Альтернатива: [предложи разрешённую альтернативу]")
                appendLine("3. Если задача НЕ нарушает инварианты — генерируй шаги как обычно, но не предлагай шагов, нарушающих инварианты.")
                appendLine()
            }

            appendLine("## Задача:")
            appendLine("Название: $taskTitle")
            appendLine("Описание: $description")
            appendLine()
            if (relevantFacts.isNotEmpty()) {
                appendLine("## Релевантные факты из базы знаний:")
                appendLine(relevantFacts)
                appendLine()
            }
            appendLine("## Инструкция:")
            appendLine("Создай список конкретных шагов для выполнения этой задачи. Каждый шаг должен быть:")
            appendLine("- Конкретным и выполнимым")
            appendLine("- Атомарным (одно действие на шаг)")
            appendLine("- Понятным без дополнительного контекста")
            appendLine()
            appendLine("Формат ответа: пронумерованный список, каждый шаг с новой строки.")
            appendLine("Пример:")
            appendLine("1. Первый шаг")
            appendLine("2. Второй шаг")
            appendLine("3. Третий шаг")
            appendLine()

            if (invariantsText.isNotEmpty()) {
                appendLine("ВАЖНО: Если задача сама по себе нарушает инварианты — НЕ генерируй шаги, а верни только отказ с ❌ и 💡.")
                appendLine()
            }

            appendLine("Сгенерируй список шагов:")
        }
    }

    // --- Статические утилиты (public для переиспользования) ---

    companion object {
        /**
         * Формирует текстовый блок [INVARIANTS] для вставки в промпт.
         */
        fun buildInvariantsBlock(invariants: List<Invariant>): String {
            val sb = StringBuilder()
            sb.appendLine("[INVARIANTS - DO NOT VIOLATE]")
            invariants.forEachIndexed { index, inv ->
                sb.appendLine("${index + 1}. ${inv.rule}")
            }
            return sb.toString()
        }
    }

    /**
     * Проверяет, содержит ли ответ LLM отказ из-за инвариантов.
     * Распознаёт маркер ❌ в ответе.
     */
    private fun isInvariantRefusal(response: String): Boolean {
        return response.contains("❌") && (
                response.contains("Нарушение инварианта") ||
                        response.contains("нарушение инварианта") ||
                        response.contains("противоречит инварианту")
                )
    }

    /**
     * Извлекает текст нарушенного инварианта из ответа LLM с отказом.
     */
    private fun extractConflictingRule(response: String): String {
        val patterns = listOf(
            Regex("""инварианту:\s*(.+?)(?:\.|$)""", RegexOption.IGNORE_CASE),
            Regex("""инвариант:\s*(.+?)(?:\.|$)""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(response)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        return "неизвестный инвариант"
    }
}
