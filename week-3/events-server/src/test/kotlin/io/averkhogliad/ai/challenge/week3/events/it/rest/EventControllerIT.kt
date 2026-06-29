package io.averkhogliad.ai.challenge.week3.events.it.rest

import io.averkhogliad.ai.challenge.week3.events.rest.dto.CreateEventRequest
import io.averkhogliad.ai.challenge.week3.events.rest.dto.UpdateEventRequest
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.*

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRestClientConfig::class)
class EventControllerIT(
    private val mockMvc: MockMvc,
    private val jdbcClient: JdbcClient,
) : FreeSpec({

    val json = Json { encodeDefaults = true }

    beforeTest {
        // Clean database before each test
        jdbcClient.sql("DELETE FROM event").update()
    }

    "POST /api/v1/events" - {

        "creates event and returns 201 with full event JSON" {
            // given
            val request = CreateEventRequest(
                date = LocalDate(2026, 6, 27),
                title = "Team Sync",
                description = "Weekly alignment",
            )

            // when
            val response = mockMvc.perform(
                post("/api/v1/events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.encodeToString(request))
            )

            // then — verify HTTP response
            response
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").isNotEmpty)
                .andExpect(jsonPath("$.date").value("2026-06-27"))
                .andExpect(jsonPath("$.title").value("Team Sync"))
                .andExpect(jsonPath("$.description").value("Weekly alignment"))

            // then — verify DB state
            val count = jdbcClient.sql("SELECT COUNT(*) FROM event WHERE title = ? AND description = ?")
                .param("Team Sync")
                .param("Weekly alignment")
                .query(Long::class.java)
                .single()
            count shouldBe 1L
        }

        "returns 400 with error JSON when title is blank" {
            // given
            val request = CreateEventRequest(
                date = LocalDate(2026, 6, 27),
                title = "",
            )

            // when
            val response = mockMvc.perform(
                post("/api/v1/events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.encodeToString(request))
            )

            // then
            response
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        }
    }

    "GET /api/v1/events" - {

        "returns paginated list with items and meta" {
            // given — seed 3 events via raw SQL
            jdbcClient.sql("INSERT INTO event (id, date, title, description, created_at) VALUES (?, ?, ?, ?, ?)")
                .param(UUID.randomUUID().toString()).param("2026-01-15").param("Event A").param("")
                .param(java.time.Instant.now().toString()).update()
            jdbcClient.sql("INSERT INTO event (id, date, title, description, created_at) VALUES (?, ?, ?, ?, ?)")
                .param(UUID.randomUUID().toString()).param("2026-02-01").param("Event B").param("")
                .param(java.time.Instant.now().toString()).update()
            jdbcClient.sql("INSERT INTO event (id, date, title, description, created_at) VALUES (?, ?, ?, ?, ?)")
                .param(UUID.randomUUID().toString()).param("2026-03-10").param("Event C").param("")
                .param(java.time.Instant.now().toString()).update()

            // when
            val response = mockMvc.perform(
                get("/api/v1/events")
                    .param("limit", "10")
                    .param("offset", "0")
            )

            // then
            response
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.meta.total").value(3))
                .andExpect(jsonPath("$.meta.limit").value(10))
                .andExpect(jsonPath("$.meta.offset").value(0))
                .andExpect(jsonPath("$.items[0].title").value("Event A"))
                .andExpect(jsonPath("$.items[1].title").value("Event B"))
                .andExpect(jsonPath("$.items[2].title").value("Event C"))
        }

        "filters by date range" {
            // given
            jdbcClient.sql("INSERT INTO event (id, date, title, description, created_at) VALUES (?, ?, ?, ?, ?)")
                .param(UUID.randomUUID().toString()).param("2026-01-10").param("Old").param("")
                .param(java.time.Instant.now().toString()).update()
            jdbcClient.sql("INSERT INTO event (id, date, title, description, created_at) VALUES (?, ?, ?, ?, ?)")
                .param(UUID.randomUUID().toString()).param("2026-06-15").param("Middle").param("")
                .param(java.time.Instant.now().toString()).update()
            jdbcClient.sql("INSERT INTO event (id, date, title, description, created_at) VALUES (?, ?, ?, ?, ?)")
                .param(UUID.randomUUID().toString()).param("2026-12-20").param("New").param("")
                .param(java.time.Instant.now().toString()).update()

            // when
            val response = mockMvc.perform(
                get("/api/v1/events")
                    .param("from_date", "2026-06-01")
                    .param("to_date", "2026-12-31")
            )

            // then
            response
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.meta.total").value(2))
                .andExpect(jsonPath("$.items[0].title").value("Middle"))
                .andExpect(jsonPath("$.items[1].title").value("New"))
        }

        "returns empty list when no events exist" {
            // given — already clean from beforeTest

            // when
            val response = mockMvc.perform(get("/api/v1/events"))

            // then
            response
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.meta.total").value(0))
        }
    }

    "GET /api/v1/events/{id}" - {

        "returns 200 with event JSON when found" {
            // given
            val id = UUID.randomUUID()
            jdbcClient.sql("INSERT INTO event (id, date, title, description, created_at) VALUES (?, ?, ?, ?, ?)")
                .param(id.toString()).param("2026-05-10").param("My Event").param("Details")
                .param(java.time.Instant.now().toString()).update()

            // when
            val response = mockMvc.perform(get("/api/v1/events/$id"))

            // then
            response
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.date").value("2026-05-10"))
                .andExpect(jsonPath("$.title").value("My Event"))
                .andExpect(jsonPath("$.description").value("Details"))
        }

        "returns 404 with error JSON when not found" {
            // given — no event with this id

            // when
            val response = mockMvc.perform(get("/api/v1/events/${UUID.randomUUID()}"))

            // then
            response
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
        }
    }

    "PATCH /api/v1/events/{id}" - {

        "updates only provided fields and returns 200" {
            // given
            val id = UUID.randomUUID()
            jdbcClient.sql("INSERT INTO event (id, date, title, description, created_at) VALUES (?, ?, ?, ?, ?)")
                .param(id.toString()).param("2026-03-01").param("Original").param("Original desc")
                .param(java.time.Instant.now().toString()).update()

            val updateRequest = UpdateEventRequest(
                title = "Updated Title",
            )

            // when
            val response = mockMvc.perform(
                patch("/api/v1/events/$id")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.encodeToString(updateRequest))
            )

            // then — verify HTTP response
            response
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.title").value("Updated Title"))
                .andExpect(jsonPath("$.date").value("2026-03-01"))
                .andExpect(jsonPath("$.description").value("Original desc"))

            // then — verify DB state
            val row = jdbcClient.sql("SELECT title, date, description FROM event WHERE id = ?")
                .param(id.toString())
                .query()
                .singleRow()
            row["title"] shouldBe "Updated Title"
            row["description"] shouldBe "Original desc" // unchanged
        }
    }

    "DELETE /api/v1/events/{id}" - {

        "deletes event and returns 204" {
            // given
            val id = UUID.randomUUID()
            jdbcClient.sql("INSERT INTO event (id, date, title, description, created_at) VALUES (?, ?, ?, ?, ?)")
                .param(id.toString()).param("2026-07-07").param("To Delete").param("")
                .param(java.time.Instant.now().toString()).update()

            // when
            val response = mockMvc.perform(delete("/api/v1/events/$id"))

            // then — HTTP response
            response.andExpect(status().isNoContent)

            // then — DB should be empty
            val count = jdbcClient.sql("SELECT COUNT(*) FROM event WHERE id = ?")
                .param(id.toString())
                .query(Long::class.java)
                .single()
            count shouldBe 0L
        }

        "returns 404 when event does not exist" {
            // when
            val response = mockMvc.perform(delete("/api/v1/events/${UUID.randomUUID()}"))

            // then
            response.andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
        }
    }
})
