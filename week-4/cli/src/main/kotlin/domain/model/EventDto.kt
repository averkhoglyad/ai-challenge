package io.averkhogliad.ai.challenge.week4.cli.domain.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.util.*

/**
 * DTO события (Event) от внешнего Events-сервиса.
 */
@Serializable
data class EventDto(
    @Contextual val id: UUID,
    val title: String,
    @Contextual val date: LocalDate,
    @Contextual val createdAt: Instant
)
