package io.averkhogliad.ai.challenge.week6.ticketserver.core.model

import java.time.Instant

data class Ticket(
    val id: String,
    val userId: String,
    val subject: String,
    val description: String,
    val status: TicketStatus,
    val priority: TicketPriority,
    val createdAt: Instant,
    val updatedAt: Instant,
    val resolution: String? = null,
)
