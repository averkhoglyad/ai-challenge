package io.averkhogliad.ai.challenge.week3.events.rest.dto

import kotlinx.serialization.Serializable

@Serializable
data class DeleteSuccessResponse(
    val deleted: String,
    val message: String = "Event deleted successfully",
)