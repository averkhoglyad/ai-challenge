package io.averkhogliad.ai.challenge.week6.ticketserver.rest.dto

data class TicketListResponse(
    val items: List<TicketResponse>,
    val meta: PaginationMeta,
)
