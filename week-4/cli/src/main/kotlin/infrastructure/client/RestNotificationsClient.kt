package io.averkhogliad.ai.challenge.week4.cli.infrastructure.client

import io.averkhogliad.ai.challenge.week4.cli.domain.config.ServerPaths
import io.averkhogliad.ai.challenge.week4.cli.domain.config.ServicesConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.ErrorResponse
import io.averkhogliad.ai.challenge.week4.cli.domain.model.NotificationDto
import io.averkhogliad.ai.challenge.week4.cli.domain.model.NotificationsException
import io.averkhogliad.ai.challenge.week4.cli.domain.model.PaginatedNotificationResponse
import io.averkhogliad.ai.challenge.week4.cli.domain.service.NotificationsClient
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * REST-адаптер для Notifications-сервиса.
 *
 * Реализует [NotificationsClient] через Ktor HttpClient.
 * Выполняет fallback-сортировку на клиенте по createdAt DESC.
 *
 * @param config конфигурация внешних сервисов
 * @param httpClient настроенный Ktor HttpClient
 */
class RestNotificationsClient(
    private val config: ServicesConfig,
    private val httpClient: HttpClient
) : NotificationsClient {

    override suspend fun list(limit: Int): Result<List<NotificationDto>> = runCatching {
        val response = httpClient.get("${config.notificationsBaseUrl}${ServerPaths.Rest.NOTIFICATIONS_API}") {
            parameter("limit", limit.coerceIn(1, 100))
        }
        if (response.status.isSuccess()) {
            val paginated = response.body<PaginatedNotificationResponse>()
            // Сортировка по createdAt DESC (fallback, если сервер не сортирует)
            paginated.items.sortedByDescending { it.createdAt }
        } else {
            throw mapNotificationsError(response)
        }
    }

    private suspend fun mapNotificationsError(response: HttpResponse): NotificationsException {
        return try {
            val errorBody = response.body<ErrorResponse>()
            NotificationsException.ServerError(errorBody.error.code, errorBody.error.message)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            NotificationsException.ConnectionFailed(e)
        }
    }
}
