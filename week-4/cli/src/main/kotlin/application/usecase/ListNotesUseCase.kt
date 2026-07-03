package io.averkhogliad.ai.challenge.week4.cli.application.usecase

import io.averkhogliad.ai.challenge.week4.cli.domain.model.NotificationDto
import io.averkhogliad.ai.challenge.week4.cli.domain.service.NotificationsClient

class ListNotesUseCase(
    private val notificationsClient: NotificationsClient
) {
    suspend fun execute(limit: Int = 20): Result<List<NotificationDto>> {
        return notificationsClient.list(limit.coerceIn(1, 100))
    }
}
