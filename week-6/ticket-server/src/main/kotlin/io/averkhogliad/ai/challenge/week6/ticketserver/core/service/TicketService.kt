package io.averkhogliad.ai.challenge.week6.ticketserver.core.service

import io.averkhogliad.ai.challenge.week6.ticketserver.core.exception.TicketNotFoundException
import io.averkhogliad.ai.challenge.week6.ticketserver.core.exception.ValidationException
import io.averkhogliad.ai.challenge.week6.ticketserver.core.model.Ticket
import io.averkhogliad.ai.challenge.week6.ticketserver.core.model.TicketStatus
import io.averkhogliad.ai.challenge.week6.ticketserver.core.repository.TicketRepository
import org.springframework.stereotype.Service

@Service
class TicketService(
    private val ticketRepository: TicketRepository,
) {

    fun getTicket(id: String): Ticket {
        return ticketRepository.findById(id)
            ?: throw TicketNotFoundException(id)
    }

    fun searchTickets(
        query: String?,
        status: String?,
        limit: Int,
        offset: Int,
    ): SearchResult {
        validatePagination(limit, offset)

        val statusFilter = status?.let {
            try {
                TicketStatus.valueOf(it.uppercase())
            } catch (_: IllegalArgumentException) {
                throw ValidationException(
                    "Invalid status: $status",
                    mapOf("status" to "Valid values: OPEN, IN_PROGRESS, RESOLVED, CLOSED"),
                )
            }
        }

        var filtered = ticketRepository.findAll().toList()

        if (statusFilter != null) {
            filtered = filtered.filter { it.status == statusFilter }
        }

        if (!query.isNullOrBlank()) {
            val lowerQuery = query.lowercase()
            filtered = filtered.filter { ticket ->
                ticket.subject.lowercase().contains(lowerQuery) ||
                        ticket.description.lowercase().contains(lowerQuery)
            }
        }

        val total = filtered.size
        val paged = filtered.drop(offset).take(limit)

        return SearchResult(items = paged, total = total)
    }

    private fun validatePagination(limit: Int, offset: Int) {
        val errors = mutableMapOf<String, String>()
        if (limit < 1 || limit > 100) {
            errors["limit"] = "Must be between 1 and 100, got $limit"
        }
        if (offset < 0) {
            errors["offset"] = "Must be >= 0, got $offset"
        }
        if (errors.isNotEmpty()) {
            throw ValidationException("Invalid pagination parameters", errors)
        }
    }

    data class SearchResult(
        val items: List<Ticket>,
        val total: Int,
    )
}
