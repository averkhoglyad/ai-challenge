package io.averkhogliad.ai.challenge.week3.notification.mcp.prompt

import org.springframework.ai.mcp.annotation.McpArg
import org.springframework.ai.mcp.annotation.McpPrompt
import org.springframework.stereotype.Component

/**
 * MCP Prompts providing reusable prompt templates for the LLM.
 *
 * Provides 2 prompt templates:
 * - send-notification — guides the LLM through sending a new notification
 * - browse-notifications — guides the LLM through browsing recent notifications
 */
@Component
class NotificationPrompts {

    @McpPrompt(
        name = "send-notification",
        description = "Шаблон для отправки нового уведомления: запрашивает заголовок и текст сообщения"
    )
    fun sendNotification(
        @McpArg(name = "title", description = "Предполагаемый заголовок уведомления (если известен)")
        title: String?,
        @McpArg(name = "message", description = "Предполагаемый текст уведомления (если известен)")
        message: String?,
    ): String = buildString {
        appendLine("Помоги пользователю отправить новое уведомление.")
        appendLine()
        appendLine("Тебе нужно выяснить следующую информацию:")
        appendLine("1. Заголовок уведомления (краткое и понятное название)")
        appendLine("2. Текст сообщения (содержание уведомления)")
        appendLine()
        if (title != null) {
            appendLine("Пользователь упомянул заголовок: $title")
        }
        if (message != null) {
            appendLine("Пользователь упомянул сообщение: $message")
        }
        appendLine()
        appendLine("После сбора всей информации:")
        appendLine("- Вызови инструмент create_notification с полученными данными")
        appendLine("- Сообщи пользователю результат (ID созданного уведомления)")
    }

    @McpPrompt(name = "browse-notifications", description = "Шаблон для просмотра последних уведомлений с пагинацией")
    fun browseNotifications(
        @McpArg(name = "limit", description = "Количество уведомлений для отображения (если указано)")
        limit: String?,
    ): String = buildString {
        appendLine("Помоги пользователю просмотреть последние уведомления.")
        appendLine()
        if (limit != null) {
            appendLine("Пользователь хочет увидеть $limit уведомлений.")
        }
        appendLine()
        appendLine("Инструкции:")
        appendLine("- Вызови list_notifications с нужным limit и offset=0")
        appendLine("- Не забудь про пагинацию: если уведомлений больше чем limit, запроси следующую страницу")
        appendLine("- Представь уведомления пользователю в удобном виде (от новых к старым)")
        appendLine("- Если уведомлений нет, сообщи что список пуст")
    }
}
