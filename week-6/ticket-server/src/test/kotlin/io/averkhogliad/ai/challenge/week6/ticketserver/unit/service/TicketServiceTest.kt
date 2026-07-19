package io.averkhogliad.ai.challenge.week6.ticketserver.unit.service

import io.averkhogliad.ai.challenge.week6.ticketserver.core.exception.TicketNotFoundException
import io.averkhogliad.ai.challenge.week6.ticketserver.core.exception.ValidationException
import io.averkhogliad.ai.challenge.week6.ticketserver.core.service.TicketService
import io.averkhogliad.ai.challenge.week6.ticketserver.core.repository.TicketRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class TicketServiceTest : FreeSpec({

    lateinit var repository: TicketRepository
    lateinit var service: TicketService

    beforeEach {
        repository = TicketRepository()
        service = TicketService(repository)
    }

    "getTicket" - {

        "returns ticket when found" {
            val result = service.getTicket("TKT-1001")
            result.id shouldBe "TKT-1001"
            result.userId shouldBe "USR-001"
        }

        "throws TicketNotFoundException when ticket not found" {
            shouldThrow<TicketNotFoundException> {
                service.getTicket("NONEXISTENT")
            }
        }
    }

    "searchTickets" - {

        "returns all tickets when no filters" {
            val result = service.searchTickets(null, null, 50, 0)
            result.total shouldBe 5
            result.items.size shouldBe 5
        }

        "filters by status" {
            val result = service.searchTickets(null, "OPEN", 50, 0)
            result.items.forEach { it.status.name shouldBe "OPEN" }
        }

        "filters by query" {
            val result = service.searchTickets("подписк", null, 50, 0)
            result.items.any { it.id == "TKT-1002" } shouldBe true
        }

        "paginates correctly" {
            val result = service.searchTickets(null, null, 2, 0)
            result.items.size shouldBe 2
            result.total shouldBe 5
        }

        "applies offset correctly" {
            val result = service.searchTickets(null, null, 2, 2)
            result.items.size shouldBe 2
            result.total shouldBe 5
        }

        "validates limit lower bound" {
            val ex = shouldThrow<ValidationException> {
                service.searchTickets(null, null, 0, 0)
            }
            ex.details shouldBe mapOf("limit" to "Must be between 1 and 100, got 0")
        }

        "validates limit upper bound" {
            val ex = shouldThrow<ValidationException> {
                service.searchTickets(null, null, 101, 0)
            }
            ex.details shouldBe mapOf("limit" to "Must be between 1 and 100, got 101")
        }

        "validates offset negative" {
            val ex = shouldThrow<ValidationException> {
                service.searchTickets(null, null, 50, -1)
            }
            ex.details shouldBe mapOf("offset" to "Must be >= 0, got -1")
        }

        "throws ValidationException for invalid status" {
            val ex = shouldThrow<ValidationException> {
                service.searchTickets(null, "INVALID", 50, 0)
            }
            ex.details shouldBe mapOf("status" to "Valid values: OPEN, IN_PROGRESS, RESOLVED, CLOSED")
        }

        "returns empty when no matches" {
            val result = service.searchTickets("nonexistentquery", null, 50, 0)
            result.total shouldBe 0
            result.items shouldBe emptyList()
        }
    }
})
