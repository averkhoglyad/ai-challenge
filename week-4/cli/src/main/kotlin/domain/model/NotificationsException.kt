package io.averkhogliad.ai.challenge.week4.cli.domain.model

/**
 * Исключения при работе с Notifications-сервисом.
 */
sealed class NotificationsException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data class ServerError(val code: String, override val message: String) : NotificationsException("[$code] $message")
    data class ConnectionFailed(override val cause: Throwable) :
        NotificationsException("Ошибка соединения: ${cause.message}", cause)
}
