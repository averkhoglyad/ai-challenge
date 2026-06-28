package io.averkhogliad.ai.challenge.week3.notification.rest.dto

import jakarta.validation.constraints.NotBlank
import kotlinx.serialization.Serializable

@Serializable
data class CreateNotificationRequest(
    @field:NotBlank
    val title: String,
    @field:NotBlank
    val message: String,
)
