package io.averkhogliad.ai.challenge.week6.ticketserver.rest.dto

import io.averkhogliad.ai.challenge.week6.ticketserver.core.model.Ticket
import java.time.format.DateTimeFormatter

data class TicketResponse(
    val id: String,
    val userId: String,
    val subject: String,
    val description: String,
    val status: String,
    val priority: String,
    val createdAt: String,
    val updatedAt: String,
    val resolution: String?,
) {
    companion object {
        private val dtf = DateTimeFormatter.ISO_INSTANT

        fun from(ticket: Ticket): TicketResponse = TicketResponse(
            id = ticket.id,
            userId = ticket.userId,
            subject = ticket.subject,
            description = ticket.description,
            status = ticket.status.name,
            priority = ticket.priority.name,
            createdAt = dtf.format(ticket.createdAt),
            updatedAt = dtf.format(ticket.updatedAt),
            resolution = ticket.resolution,
        )
    }
}
