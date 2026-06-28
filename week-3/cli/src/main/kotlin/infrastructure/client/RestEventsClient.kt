package io.averkhogliad.ai.challenge.week3.cli.infrastructure.client

import io.averkhogliad.ai.challenge.week3.cli.domain.config.ServerPaths
import io.averkhogliad.ai.challenge.week3.cli.domain.config.ServicesConfig
import io.averkhogliad.ai.challenge.week3.cli.domain.model.ErrorResponse
import io.averkhogliad.ai.challenge.week3.cli.domain.model.EventDto
import io.averkhogliad.ai.challenge.week3.cli.domain.model.EventsException
import io.averkhogliad.ai.challenge.week3.cli.domain.service.EventsClient
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.*

/**
 * REST-адаптер для Events-сервиса.
 *
 * Реализует [EventsClient] через Ktor HttpClient.
 * Маппит HTTP-ошибки в [EventsException] согласно spec.
 *
 * @param config конфигурация внешних сервисов
 * @param httpClient настроенный Ktor HttpClient
 */
class RestEventsClient(
    private val config: ServicesConfig,
    private val httpClient: HttpClient
) : EventsClient {

    override suspend fun createEvent(title: String, date: LocalDate): Result<EventDto> = runCatching {
        val response = httpClient.post("${config.eventsBaseUrl}${ServerPaths.Rest.EVENTS_API}") {
            contentType(ContentType.Application.Json)
            setBody(CreateEventRequest(title = title, date = date))
        }
        if (response.status.isSuccess()) {
            response.body<EventDto>()
        } else {
            throw mapEventsError(response)
        }
    }

    override suspend fun deleteEvent(id: UUID): Result<Unit> = runCatching {
        val response = httpClient.delete("${config.eventsBaseUrl}${ServerPaths.Rest.EVENTS_API}/$id")
        if (!response.status.isSuccess()) {
            throw mapEventsError(response)
        }
    }

    private suspend fun mapEventsError(response: HttpResponse): EventsException {
        return try {
            val errorBody = response.body<ErrorResponse>()
            when (response.status.value) {
                400 -> EventsException.ValidationFailed(errorBody.error.message)
                404 -> EventsException.EventNotFound(extractIdFromResponse(response))
                500 -> EventsException.ServerError(errorBody.error.code, errorBody.error.message)
                else -> EventsException.ServerError("UNKNOWN", "HTTP ${response.status.value}")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            EventsException.ConnectionFailed(e)
        }
    }

    private fun extractIdFromResponse(response: HttpResponse): UUID {
        val path = response.call.request.url.encodedPath
        val segments = path.split("/")
        return try {
            UUID.fromString(segments.last())
        } catch (_: IllegalArgumentException) {
            UUID.randomUUID()
        }
    }
}

/**
 * Request body для POST /api/v1/events.
 */
@Serializable
data class CreateEventRequest(
    val title: String,
    @Contextual val date: LocalDate
)
