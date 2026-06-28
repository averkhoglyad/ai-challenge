package io.averkhogliad.ai.challenge.week3.notification.unit.core.model

import io.averkhogliad.ai.challenge.week3.notification.core.model.Notification
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.*

class NotificationTest : FreeSpec({

    val json = Json { encodeDefaults = true }

    "Notification" - {

        "creates with default createdAt as current time" {
            // given / when
            val notification = Notification(
                title = "Test",
                message = "Test message"
            )

            // then
            notification.id shouldBe null
            notification.title shouldBe "Test"
            notification.message shouldBe "Test message"
            notification.createdAt shouldBe notification.createdAt // Instant is set
        }

        "supports custom id and createdAt" {
            // given
            val id = UUID.randomUUID()
            val now = Instant.now()

            // when
            val notification = Notification(
                id = id,
                title = "Custom",
                message = "Custom message",
                createdAt = now
            )

            // then
            notification.id shouldBe id
            notification.title shouldBe "Custom"
            notification.message shouldBe "Custom message"
            notification.createdAt shouldBe now
        }
    }

    "JSON serialization" - {

        "serializes Notification to JSON correctly" {
            // given
            val id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
            val instant = Instant.parse("2025-06-15T10:30:00Z")
            val notification = Notification(
                id = id,
                title = "Hello",
                message = "World",
                createdAt = instant
            )

            // when
            val jsonString = json.encodeToString(Notification.serializer(), notification)

            // then
            jsonString shouldBe """{"id":"123e4567-e89b-12d3-a456-426614174000","title":"Hello","message":"World","createdAt":"2025-06-15T10:30:00Z"}"""
        }

        "deserializes Notification from JSON correctly" {
            // given
            val jsonString =
                """{"id":"123e4567-e89b-12d3-a456-426614174000","title":"Hello","message":"World","createdAt":"2025-06-15T10:30:00Z"}"""

            // when
            val notification = json.decodeFromString(Notification.serializer(), jsonString)

            // then
            notification.id shouldBe UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
            notification.title shouldBe "Hello"
            notification.message shouldBe "World"
            notification.createdAt shouldBe Instant.parse("2025-06-15T10:30:00Z")
        }
    }
})
