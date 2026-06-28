package io.averkhogliad.ai.challenge.week3.cli.domain.service

import io.averkhogliad.ai.challenge.week3.cli.domain.model.NotificationDto

/**
 * Порт для взаимодействия с Notifications-сервисом.
 */
interface NotificationsClient {
    suspend fun list(limit: Int = 20): Result<List<NotificationDto>>
}
