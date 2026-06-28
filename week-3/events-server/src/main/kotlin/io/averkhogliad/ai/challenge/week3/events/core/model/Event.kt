package io.averkhogliad.ai.challenge.week3.events.core.model

import io.averkhogliad.ai.challenge.week3.events.infra.serialization.InstantSerializer
import io.averkhogliad.ai.challenge.week3.events.infra.serialization.LocalDateSerializer
import io.averkhogliad.ai.challenge.week3.events.infra.serialization.UUIDSerializer
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.*

@Table("event")
@Serializable
data class Event(
    @Id
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate,
    val title: String,
    val description: String = "",
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant = Instant.now(),
)
