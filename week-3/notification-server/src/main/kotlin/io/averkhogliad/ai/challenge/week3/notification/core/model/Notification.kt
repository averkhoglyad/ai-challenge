package io.averkhogliad.ai.challenge.week3.notification.core.model

import io.averkhogliad.ai.challenge.week3.notification.infra.serialization.InstantSerializer
import io.averkhogliad.ai.challenge.week3.notification.infra.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.*

@Table("notification")
@Serializable
data class Notification(
    @Id
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val title: String,
    val message: String,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant = Instant.now(),
)
