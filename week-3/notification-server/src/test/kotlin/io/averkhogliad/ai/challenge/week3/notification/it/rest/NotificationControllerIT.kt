package io.averkhogliad.ai.challenge.week3.notification.it.rest

import io.averkhogliad.ai.challenge.week3.notification.rest.dto.CreateNotificationRequest
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.*

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationControllerIT(
    private val mockMvc: MockMvc,
    private val jdbcClient: JdbcClient,
) : FreeSpec({

    val json = Json { encodeDefaults = true }

    beforeTest {
        // Clean database before each test
        jdbcClient.sql("DELETE FROM notification").update()
    }

    "POST /api/v1/notifications" - {

        "creates notification and returns 201 with full notification JSON" {
            // given
            val request = CreateNotificationRequest(
                title = "System Alert",
                message = "Disk space low",
            )

            // when
            val response = mockMvc.perform(
                post("/api/v1/notifications")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.encodeToString(request))
            )

            // then — verify HTTP response
            response
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").isNotEmpty)
                .andExpect(jsonPath("$.title").value("System Alert"))
                .andExpect(jsonPath("$.message").value("Disk space low"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty)

            // then — verify DB state
            val count = jdbcClient.sql("SELECT COUNT(*) FROM notification WHERE title = ? AND message = ?")
                .param("System Alert")
                .param("Disk space low")
                .query(Long::class.java)
                .single()
            count shouldBe 1L
        }

        "returns 400 with error JSON when title is blank" {
            // given
            val request = CreateNotificationRequest(
                title = "",
                message = "Some message",
            )

            // when
            val response = mockMvc.perform(
                post("/api/v1/notifications")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.encodeToString(request))
            )

            // then
            response
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        }

        "returns 400 with error JSON when message is blank" {
            // given
            val request = CreateNotificationRequest(
                title = "Title",
                message = "",
            )

            // when
            val response = mockMvc.perform(
                post("/api/v1/notifications")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.encodeToString(request))
            )

            // then
            response
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        }
    }

    "GET /api/v1/notifications" - {

        "returns paginated list with items and meta sorted by created_at DESC" {
            // given — seed 3 notifications via raw SQL
            jdbcClient.sql("INSERT INTO notification (id, title, message, created_at) VALUES (?, ?, ?, ?)")
                .param(UUID.randomUUID().toString()).param("Notif A").param("Msg A").param("2025-01-15T10:00:00Z")
                .update()
            jdbcClient.sql("INSERT INTO notification (id, title, message, created_at) VALUES (?, ?, ?, ?)")
                .param(UUID.randomUUID().toString()).param("Notif B").param("Msg B").param("2025-02-01T10:00:00Z")
                .update()
            jdbcClient.sql("INSERT INTO notification (id, title, message, created_at) VALUES (?, ?, ?, ?)")
                .param(UUID.randomUUID().toString()).param("Notif C").param("Msg C").param("2025-03-10T10:00:00Z")
                .update()

            // when
            val response = mockMvc.perform(
                get("/api/v1/notifications")
                    .param("limit", "10")
                    .param("offset", "0")
            )

            // then — newest first (DESC)
            response
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.meta.total").value(3))
                .andExpect(jsonPath("$.meta.limit").value(10))
                .andExpect(jsonPath("$.meta.offset").value(0))
                .andExpect(jsonPath("$.items[0].title").value("Notif C"))
                .andExpect(jsonPath("$.items[1].title").value("Notif B"))
                .andExpect(jsonPath("$.items[2].title").value("Notif A"))
        }

        "respects limit and offset" {
            // given
            jdbcClient.sql("INSERT INTO notification (id, title, message, created_at) VALUES (?, ?, ?, ?)")
                .param(UUID.randomUUID().toString()).param("Notif A").param("Msg A").param("2025-01-15T10:00:00Z")
                .update()
            jdbcClient.sql("INSERT INTO notification (id, title, message, created_at) VALUES (?, ?, ?, ?)")
                .param(UUID.randomUUID().toString()).param("Notif B").param("Msg B").param("2025-02-01T10:00:00Z")
                .update()
            jdbcClient.sql("INSERT INTO notification (id, title, message, created_at) VALUES (?, ?, ?, ?)")
                .param(UUID.randomUUID().toString()).param("Notif C").param("Msg C").param("2025-03-10T10:00:00Z")
                .update()

            // when — limit=2, offset=1
            val response = mockMvc.perform(
                get("/api/v1/notifications")
                    .param("limit", "2")
                    .param("offset", "1")
            )

            // then
            response
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.meta.total").value(3))
                .andExpect(jsonPath("$.meta.limit").value(2))
                .andExpect(jsonPath("$.meta.offset").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Notif B"))
                .andExpect(jsonPath("$.items[1].title").value("Notif A"))
        }

        "returns empty list when no notifications exist" {
            // given — already clean from beforeTest

            // when
            val response = mockMvc.perform(get("/api/v1/notifications"))

            // then
            response
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.meta.total").value(0))
        }
    }
})
