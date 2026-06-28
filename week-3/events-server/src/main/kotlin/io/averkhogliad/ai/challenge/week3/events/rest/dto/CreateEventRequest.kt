package io.averkhogliad.ai.challenge.week3.events.rest.dto

import io.averkhogliad.ai.challenge.week3.events.infra.serialization.LocalDateSerializer
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class CreateEventRequest(
    @field:NotNull
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate,
    @field:NotBlank
    val title: String,
    val description: String = "",
)
