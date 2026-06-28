package io.averkhogliad.ai.challenge.week3.notification.rest.dto

import io.averkhogliad.ai.challenge.week3.notification.core.model.Notification
import kotlinx.serialization.Serializable

@Serializable
data class PaginationMeta(
    val total: Long,
    val limit: Int,
    val offset: Int,
)

@Serializable
data class PaginatedResponse(
    val items: List<Notification>,
    val meta: PaginationMeta,
)
