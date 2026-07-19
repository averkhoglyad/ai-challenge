package io.averkhogliad.ai.challenge.week6.ticketserver.it.controller

import io.kotest.core.spec.style.FreeSpec
import org.hamcrest.Matchers
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TicketControllerIT(
    private val mockMvc: MockMvc,
) : FreeSpec({

    "GET /api/v1/tickets/{id}" - {

        "returns 200 with valid ticket" {
            mockMvc.perform(get("/api/v1/tickets/TKT-1001"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value("TKT-1001"))
                .andExpect(jsonPath("$.userId").value("USR-001"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.subject").isString)
                .andExpect(jsonPath("$.description").isString)
                .andExpect(jsonPath("$.createdAt").isString)
                .andExpect(jsonPath("$.updatedAt").isString)
        }

        "returns 404 when ticket not found" {
            mockMvc.perform(get("/api/v1/tickets/NONEXISTENT"))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Ticket not found: NONEXISTENT"))
        }
    }

    "GET /api/v1/tickets" - {

        "returns 200 with default pagination" {
            mockMvc.perform(get("/api/v1/tickets"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.meta.total").value(5))
                .andExpect(jsonPath("$.meta.limit").value(50))
                .andExpect(jsonPath("$.meta.offset").value(0))
        }

        "filters by status" {
            mockMvc.perform(get("/api/v1/tickets").param("status", "OPEN"))
                .andExpect(status().isOk)
                .andExpect(
                    jsonPath("$.items[*].status")
                        .value(Matchers.everyItem(Matchers.`is`("OPEN")))
                )
        }

        "paginates with limit" {
            mockMvc.perform(
                get("/api/v1/tickets")
                    .param("limit", "2")
                    .param("offset", "0")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.meta.total").value(5))
        }

        "returns 400 for invalid limit" {
            mockMvc.perform(get("/api/v1/tickets").param("limit", "0"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        }
    }

    "GET /api/v1/users/{id}/context" - {

        "returns 200 with user context" {
            mockMvc.perform(get("/api/v1/users/USR-001/context"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.userId").value("USR-001"))
                .andExpect(jsonPath("$.name").value("Алексей Петров"))
                .andExpect(jsonPath("$.email").value("alexey@example.com"))
                .andExpect(jsonPath("$.company").value("ООО Технологии"))
                .andExpect(jsonPath("$.subscriptionTier").value("Pro"))
                .andExpect(jsonPath("$.openTickets").isNumber)
                .andExpect(jsonPath("$.totalTickets").isNumber)
        }

        "returns 404 when user not found" {
            mockMvc.perform(get("/api/v1/users/NONEXISTENT/context"))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
        }
    }
})
