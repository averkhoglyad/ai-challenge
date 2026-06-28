package io.averkhogliad.ai.challenge.week3.events.mcp.tool

import io.averkhogliad.ai.challenge.week3.events.core.model.Event
import io.averkhogliad.ai.challenge.week3.events.core.service.EventService
import io.averkhogliad.ai.challenge.week3.events.rest.dto.*
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component
import java.util.*

/**
 * MCP Tools for calendar event management.
 *
 * Provides 5 tools that an LLM can call via the MCP protocol:
 * create_event, list_events, get_event_details, update_event, delete_event.
 */
@Component
class EventTools(
    private val eventService: EventService,
) {
    private val json = Json { encodeDefaults = true }

    @McpTool(
        name = "create_event",
        description = "Создает новое событие в календаре с указанной датой, заголовком и опциональным описанием"
    )
    fun createEvent(
        @McpToolParam(description = "Дата события в формате YYYY-MM-DD")
        date: LocalDate,
        @McpToolParam(description = "Заголовок события (название встречи, задачи или напоминания)")
        title: String,
        @McpToolParam(description = "Необязательное описание события с дополнительными деталями")
        description: String?,
    ): String = try {
        val result = eventService.createEvent(CreateEventRequest(date, title, description ?: ""))
        json.encodeToString(Event.serializer(), result)
    } catch (e: Exception) {
        json.encodeToString(ErrorResponse.serializer(), ErrorResponse.internalError(e.message ?: "Unknown error"))
    }

    @McpTool(
        name = "list_events",
        description = "Возвращает список событий с пагинацией и фильтрацией по диапазону дат"
    )
    fun listEvents(
        @McpToolParam(description = "Начальная дата для фильтрации в формате YYYY-MM-DD. Если не указана, фильтрация по началу периода не применяется.")
        fromDate: LocalDate?,
        @McpToolParam(description = "Конечная дата для фильтрации в формате YYYY-MM-DD. Если не указана, фильтрация по концу периода не применяется.")
        toDate: LocalDate?,
        @McpToolParam(description = "Максимальное количество возвращаемых событий. По умолчанию 20.")
        limit: Int?,
        @McpToolParam(description = "Смещение для пагинации (сколько событий пропустить). По умолчанию 0.")
        offset: Int?,
    ): String = try {
        val result = eventService.listEvents(
            fromDate = fromDate,
            toDate = toDate,
            limit = limit ?: 20,
            offset = offset ?: 0,
        )
        json.encodeToString(PaginatedResponse.serializer(), result)
    } catch (e: Exception) {
        json.encodeToString(ErrorResponse.serializer(), ErrorResponse.internalError(e.message ?: "Unknown error"))
    }

    @McpTool(name = "get_event_details", description = "Возвращает подробную информацию о событии по его UUID")
    fun getEventDetails(
        @McpToolParam(description = "UUID события, информацию о котором нужно получить")
        id: UUID,
    ): String = try {
        val result = eventService.getEvent(id)
        json.encodeToString(Event.serializer(), result)
    } catch (e: Exception) {
        json.encodeToString(ErrorResponse.serializer(), ErrorResponse.internalError(e.message ?: "Unknown error"))
    }

    @McpTool(
        name = "update_event",
        description = "Обновляет существующее событие. Все поля опциональны — передаются только изменяемые."
    )
    fun updateEvent(
        @McpToolParam(description = "UUID события, которое нужно изменить")
        id: UUID,
        @McpToolParam(description = "Новая дата события в формате YYYY-MM-DD. Не передавай, если дата не меняется.")
        date: LocalDate?,
        @McpToolParam(description = "Новый заголовок события. Не передавай, если заголовок не меняется.")
        title: String?,
        @McpToolParam(description = "Новое описание события. Не передавай, если описание не меняется.")
        description: String?,
    ): String = try {
        val request = UpdateEventRequest(date = date, title = title, description = description)
        val result = eventService.updateEvent(id, request)
        json.encodeToString(Event.serializer(), result)
    } catch (e: Exception) {
        json.encodeToString(ErrorResponse.serializer(), ErrorResponse.internalError(e.message ?: "Unknown error"))
    }

    @McpTool(name = "delete_event", description = "Удаляет событие из календаря по его UUID")
    fun deleteEvent(
        @McpToolParam(description = "UUID события, которое нужно удалить")
        id: UUID,
    ): String = try {
        eventService.deleteEvent(id)
        json.encodeToString(DeleteSuccessResponse.serializer(), DeleteSuccessResponse(id.toString()))
    } catch (e: Exception) {
        json.encodeToString(ErrorResponse.serializer(), ErrorResponse.internalError(e.message ?: "Unknown error"))
    }
}

