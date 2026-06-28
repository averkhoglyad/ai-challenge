package io.averkhogliad.ai.challenge.week3.notification.mcp.tool

import io.averkhogliad.ai.challenge.week3.notification.core.model.Notification
import io.averkhogliad.ai.challenge.week3.notification.core.service.NotificationService
import io.averkhogliad.ai.challenge.week3.notification.rest.dto.CreateNotificationRequest
import io.averkhogliad.ai.challenge.week3.notification.rest.dto.ErrorResponse
import io.averkhogliad.ai.challenge.week3.notification.rest.dto.PaginatedResponse
import kotlinx.serialization.json.Json
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

/**
 * MCP Tools for notification management.
 *
 * Provides 2 tools that an LLM can call via the MCP protocol:
 * create_notification, list_notifications.
 */
@Component
class NotificationTools(
    private val notificationService: NotificationService,
) {
    private val json = Json { encodeDefaults = true }

    @McpTool(
        name = "create_notification",
        description = "Создает новое уведомление с указанным заголовком и сообщением"
    )
    fun createNotification(
        @McpToolParam(description = "Заголовок уведомления")
        title: String,
        @McpToolParam(description = "Текст сообщения уведомления")
        message: String,
    ): String = try {
        val result = notificationService.createNotification(CreateNotificationRequest(title, message))
        json.encodeToString(Notification.serializer(), result)
    } catch (e: Exception) {
        json.encodeToString(ErrorResponse.serializer(), ErrorResponse.internalError(e.message ?: "Unknown error"))
    }

    @McpTool(
        name = "list_notifications",
        description = "Возвращает список уведомлений с пагинацией, отсортированных по времени создания (новые первыми)"
    )
    fun listNotifications(
        @McpToolParam(description = "Максимальное количество возвращаемых уведомлений. По умолчанию 20.")
        limit: Int?,
        @McpToolParam(description = "Смещение для пагинации (сколько уведомлений пропустить). По умолчанию 0.")
        offset: Int?,
    ): String = try {
        val result = notificationService.listNotifications(
            limit = limit ?: 50,
            offset = offset ?: 0,
        )
        json.encodeToString(PaginatedResponse.serializer(), result)
    } catch (e: Exception) {
        json.encodeToString(ErrorResponse.serializer(), ErrorResponse.internalError(e.message ?: "Unknown error"))
    }
}
