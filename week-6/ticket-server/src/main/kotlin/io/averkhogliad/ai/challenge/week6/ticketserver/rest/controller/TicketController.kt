package io.averkhogliad.ai.challenge.week6.ticketserver.rest.controller

import io.averkhogliad.ai.challenge.week6.ticketserver.core.service.TicketService
import io.averkhogliad.ai.challenge.week6.ticketserver.rest.dto.PaginationMeta
import io.averkhogliad.ai.challenge.week6.ticketserver.rest.dto.TicketListResponse
import io.averkhogliad.ai.challenge.week6.ticketserver.rest.dto.TicketResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/tickets")
class TicketController(
    private val ticketService: TicketService,
) {

    @GetMapping("/{id}")
    fun getTicket(@PathVariable id: String): ResponseEntity<TicketResponse> {
        val ticket = ticketService.getTicket(id)
        return ResponseEntity.ok(TicketResponse.from(ticket))
    }

    @GetMapping
    fun searchTickets(
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
    ): ResponseEntity<TicketListResponse> {
        val result = ticketService.searchTickets(query, status, limit, offset)
        return ResponseEntity.ok(
            TicketListResponse(
                items = result.items.map(TicketResponse::from),
                meta = PaginationMeta(
                    total = result.total,
                    limit = limit,
                    offset = offset,
                ),
            )
        )
    }
}
