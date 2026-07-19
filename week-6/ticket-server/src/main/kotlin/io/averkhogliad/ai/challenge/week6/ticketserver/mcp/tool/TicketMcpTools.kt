package io.averkhogliad.ai.challenge.week6.ticketserver.mcp.tool

import io.averkhogliad.ai.challenge.week6.ticketserver.core.service.TicketService
import io.averkhogliad.ai.challenge.week6.ticketserver.core.service.UserService
import io.averkhogliad.ai.challenge.week6.ticketserver.rest.dto.PaginationMeta
import io.averkhogliad.ai.challenge.week6.ticketserver.rest.dto.TicketListResponse
import io.averkhogliad.ai.challenge.week6.ticketserver.rest.dto.TicketResponse
import io.averkhogliad.ai.challenge.week6.ticketserver.rest.dto.UserContextResponse
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

@Component
class TicketMcpTools(
    private val ticketService: TicketService,
    private val userService: UserService,
) {

    @Tool(description = "Get ticket by ID. Returns full ticket details including status, priority, and resolution.")
    fun getTicket(
        @ToolParam(description = "Ticket ID, e.g. TKT-1001") id: String,
    ): TicketResponse {
        val ticket = ticketService.getTicket(id)
        return TicketResponse.from(ticket)
    }

    @Tool(
        description = "Search tickets by query and optional status filter. " +
                "Returns paginated list with metadata. " +
                "Valid statuses: OPEN, IN_PROGRESS, RESOLVED, CLOSED."
    )
    fun searchTickets(
        @ToolParam(description = "Search query — matches subject and description") query: String? = null,
        @ToolParam(description = "Filter by ticket status (OPEN, IN_PROGRESS, RESOLVED, CLOSED)") status: String? = null,
        @ToolParam(description = "Maximum items to return (1-100)") limit: Int = 50,
        @ToolParam(description = "Offset for pagination") offset: Int = 0,
    ): TicketListResponse {
        val result = ticketService.searchTickets(query, status, limit, offset)
        return TicketListResponse(
            items = result.items.map(TicketResponse::from),
            meta = PaginationMeta(
                total = result.total,
                limit = limit,
                offset = offset,
            ),
        )
    }

    @Tool(description = "Get user context by user ID. Returns user profile and their ticket summary.")
    fun getUserContext(
        @ToolParam(description = "User ID, e.g. USR-001") userId: String,
    ): UserContextResponse {
        val context = userService.getUserContext(userId)
        return UserContextResponse.from(context)
    }
}
