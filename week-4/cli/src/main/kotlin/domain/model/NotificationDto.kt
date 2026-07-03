package io.averkhogliad.ai.challenge.week4.cli.domain.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.*

/**
 * DTO уведомления (Notification) от внешнего Notifications-сервиса.
 */
@Serializable
data class NotificationDto(
    @Contextual val id: UUID,
    val title: String,
    val message: String,
    @Contextual val createdAt: Instant
)

/**
 * Обёртка пагинированного ответа от Notifications-сервиса.
 * Соответствует формату: {"items":[],"meta":{"total":0,"limit":20,"offset":0}}
 */
@Serializable
data class PaginatedNotificationResponse(
    val items: List<NotificationDto>,
    val meta: PaginationMeta
)

@Serializable
data class PaginationMeta(
    val total: Long,
    val limit: Int,
    val offset: Int
)
