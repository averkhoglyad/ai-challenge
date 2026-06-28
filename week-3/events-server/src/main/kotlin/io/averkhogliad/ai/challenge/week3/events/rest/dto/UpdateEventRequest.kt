package io.averkhogliad.ai.challenge.week3.events.rest.dto

import io.averkhogliad.ai.challenge.week3.events.infra.serialization.LocalDateSerializer
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class UpdateEventRequest(
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate? = null,
    val title: String? = null,
    val description: String? = null,
)
