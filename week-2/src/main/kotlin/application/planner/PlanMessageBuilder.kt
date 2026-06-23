package io.averkhogliad.ai.challenge.week2.application.planner

import io.averkhogliad.ai.challenge.week2.domain.service.CommandEngine

/**
 * Формирует текстовые сообщения для PlanCommandHandler.
 *
 * Вынесен в отдельный класс для уменьшения размера PlanCommandHandler
 * и устранения God Object антипаттерна.
 */
class PlanMessageBuilder(
    private val commandEngine: CommandEngine
) {
    fun buildPlanningReadyMessage(title: String, description: String?, invariantsCount: Int): String =
        buildString {
            appendLine("✅ Этап PLANNING завершён для задачи '$title'.")
            appendLine()
            appendLine("📋 Контекст собран:")
            appendLine("• Description: ${description ?: "(не указан)"}")
            appendLine("• Релевантных фактов из LTM: ${commandEngine.getContext("factsCount") ?: "0"}")
            appendLine("• Активных инвариантов: $invariantsCount")
            val factsText = commandEngine.getContext("relevantFacts") ?: ""
            if (factsText.isNotEmpty()) {
                appendLine()
                appendLine("📚 Релевантные факты:")
                factsText.lines().take(5).forEach { appendLine("  $it") }
                val total = factsText.lines().size
                if (total > 5) appendLine("  ... и ещё ${total - 5} фактов")
            }
            appendLine()
            appendLine("🚀 Переход к этапу EXECUTION: формирование промпта для LLM...")
        }

    fun buildValidationMessage(steps: List<String>): String =
        buildString {
            appendLine("✅ Этап EXECUTION завершён. LLM сгенерировала ${steps.size} шагов.")
            appendLine()
            appendLine("📋 Предлагаемые шаги:")
            steps.forEachIndexed { i, s -> appendLine("${i + 1}. $s") }
            appendLine()
            appendLine("🔍 Переход к этапу VALIDATION.")
            appendLine("Подтвердите план (y), отмените (n) или отредактируйте (edit):")
        }

    fun buildInvariantConflictMessage(taskTitle: String, conflictingRule: String): String =
        buildString {
            appendLine("⚠️ Обнаружен конфликт с инвариантом!")
            appendLine()
            appendLine("Задача «$taskTitle» противоречит инварианту:")
            appendLine("\"$conflictingRule\"")
            appendLine()
            appendLine("Я не могу составить план для этой задачи, пока инвариант активен.")
            appendLine()
            appendLine("Варианты:")
            appendLine("1. Изменить задачу через :edit или :describe")
            appendLine("2. Удалить инвариант через :invariant remove <id>")
        }

    fun buildExecutionErrorMessage(errorMessage: String): String =
        buildString {
            appendLine("Ошибка: $errorMessage")
            appendLine()
            appendLine("Доступные действия:")
            appendLine("  :goto PLANNING  — откатиться на этап планирования (контекст сохранится)")
            appendLine("  :goto           — посмотреть карту состояний")
            appendLine("  :abort          — прервать команду")
        }
}
