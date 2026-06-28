package io.averkhogliad.ai.challenge.week3.cli.domain.model

import java.util.*

/**
 * Исключения при работе с Events-сервисом.
 */
sealed class EventsException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data class EventNotFound(val id: UUID) : EventsException("Событие не найдено: $id")
    data class ValidationFailed(override val message: String) : EventsException(message)
    data class ServerError(val code: String, override val message: String) : EventsException("[$code] $message")
    data class ConnectionFailed(override val cause: Throwable) :
        EventsException("Ошибка соединения: ${cause.message}", cause)
}
