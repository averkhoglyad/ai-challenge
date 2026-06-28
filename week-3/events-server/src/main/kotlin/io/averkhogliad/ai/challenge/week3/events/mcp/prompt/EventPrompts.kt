package io.averkhogliad.ai.challenge.week3.events.mcp.prompt

import org.springframework.ai.mcp.annotation.McpArg
import org.springframework.ai.mcp.annotation.McpPrompt
import org.springframework.stereotype.Component

/**
 * MCP Prompts providing reusable prompt templates for the LLM.
 *
 * Provides 2 prompt templates:
 * - plan-meeting — guides the LLM through planning a new meeting
 * - weekly-summary — guides the LLM through generating a weekly event summary
 */
@Component
class EventPrompts {

    @McpPrompt(
        name = "plan-meeting",
        description = "Шаблон для планирования новой встречи: запрашивает дату, заголовок, участников и длительность"
    )
    fun planMeeting(
        @McpArg(name = "date", description = "Предполагаемая дата встречи (если известна)")
        date: String?,
        @McpArg(name = "title", description = "Предполагаемый заголовок встречи (если известен)")
        title: String?,
    ): String = buildString {
        appendLine("Помоги пользователю запланировать новую встречу.")
        appendLine()
        appendLine("Тебе нужно выяснить следующую информацию:")
        appendLine("1. Дату встречи в формате YYYY-MM-DD (например, 2025-06-15)")
        appendLine("2. Заголовок встречи (краткое и понятное название)")
        appendLine("3. Список участников (имена или роли)")
        appendLine("4. Предполагаемую длительность встречи")
        appendLine()
        if (date != null) {
            appendLine("Пользователь упомянул дату: $date")
        }
        if (title != null) {
            appendLine("Пользователь упомянул заголовок: $title")
        }
        appendLine()
        appendLine("После сбора всей информации:")
        appendLine("- Вычисли точную дату в формате YYYY-MM-DD (если пользователь сказал 'завтра', вычисли: 2025-06-15)")
        appendLine("- Вызови инструмент create_event с полученными данными")
        appendLine("- В описании события укажи участников и длительность")
        appendLine("- Сообщи пользователю результат")
    }

    @McpPrompt(
        name = "weekly-summary",
        description = "Шаблон для получения сводки событий за неделю: запрашивает дату начала недели"
    )
    fun weeklySummary(
        @McpArg(name = "weekStartDate", description = "Дата начала недели в ISO 8601 (если известна)")
        weekStartDate: String?,
    ): String = buildString {
        appendLine("Помоги пользователю получить сводку событий за неделю.")
        appendLine()
        if (weekStartDate != null) {
            appendLine("Пользователь указал начало недели: $weekStartDate")
            appendLine()
            appendLine("Используй эту дату как fromDate.")
            appendLine("Вычисли toDate как fromDate + 7 дней.")
        } else {
            appendLine("Спроси пользователя, с какой даты начать неделю.")
            appendLine("Если пользователь сказал 'эта неделя' или 'текущая неделя':")
            appendLine("- Определи дату последнего понедельника как начало недели")
            appendLine("- Вычисли конец недели как понедельник + 7 дней")
            appendLine("- Преобразуй обе даты в формат YYYY-MM-DD")
        }
        appendLine()
        appendLine("После определения периода:")
        appendLine("- Вызови list_events с вычисленными fromDate и toDate")
        appendLine("- Не забудь про пагинацию: если событий больше 50, запроси следующую страницу")
        appendLine("- Сгруппируй события по дням недели")
        appendLine("- Представь сводку пользователю в удобном виде")
        appendLine("- Если событий нет, сообщи что неделя свободна")
    }
}
