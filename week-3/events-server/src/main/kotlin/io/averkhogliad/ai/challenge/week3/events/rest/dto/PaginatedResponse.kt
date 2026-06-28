package io.averkhogliad.ai.challenge.week3.events.rest.dto

import io.averkhogliad.ai.challenge.week3.events.core.model.Event
import kotlinx.serialization.Serializable

@Serializable
data class PaginationMeta(
    val total: Long,
    val limit: Int,
    val offset: Int,
)

@Serializable
data class PaginatedResponse(
    val items: List<Event>,
    val meta: PaginationMeta,
)
