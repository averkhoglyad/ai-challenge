package io.averkhogliad.ai.challenge.week3.events.unit.rest

import io.averkhogliad.ai.challenge.week3.events.core.exception.EventNotFoundException
import io.averkhogliad.ai.challenge.week3.events.core.model.Event
import io.averkhogliad.ai.challenge.week3.events.core.repository.EventRepository
import io.averkhogliad.ai.challenge.week3.events.core.service.EventService
import io.averkhogliad.ai.challenge.week3.events.infra.client.NotificationClient
import io.averkhogliad.ai.challenge.week3.events.rest.dto.CreateEventRequest
import io.averkhogliad.ai.challenge.week3.events.rest.dto.PaginatedResponse
import io.averkhogliad.ai.challenge.week3.events.rest.dto.PaginationMeta
import io.averkhogliad.ai.challenge.week3.events.rest.dto.UpdateEventRequest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.datetime.LocalDate
import java.util.*
import kotlin.time.Clock

class EventServiceTest : FreeSpec({

    lateinit var repository: EventRepository
    lateinit var notificationClient: NotificationClient
    lateinit var service: EventService

    beforeTest {
        repository = mockk()
        notificationClient = mockk(relaxed = true)
        service = EventService(repository, notificationClient, Clock.System)
    }

    "createEvent" - {

        "saves event via repository and returns it" {
            // given
            val request = CreateEventRequest(
                date = LocalDate(2026, 6, 27),
                title = "Team Meeting",
                description = "Weekly sync"
            )
            every { repository.save(any<Event>()) } answers { firstArg() }

            // when
            val result = service.createEvent(request)

            // then
            result.date shouldBe request.date
            result.title shouldBe request.title
            result.description shouldBe request.description
            verify(exactly = 1) { repository.save(any<Event>()) }
            verify(exactly = 1) { notificationClient.send("Событие создано", any()) }
        }
    }

    "getEvent" - {

        "returns event when found" {
            // given
            val eventId = UUID.randomUUID()
            val event = Event(
                id = eventId,
                date = LocalDate(2026, 6, 27),
                title = "Conference",
                description = ""
            )
            every { repository.findById(eventId) } returns Optional.of(event)

            // when
            val result = service.getEvent(eventId)

            // then
            result shouldBe event
        }

        "throws EventNotFoundException when not found" {
            // given
            val eventId = UUID.randomUUID()
            every { repository.findById(eventId) } returns Optional.empty()

            // when / then
            shouldThrow<EventNotFoundException> {
                service.getEvent(eventId)
            }
        }
    }

    "listEvents" - {

        "returns PaginatedResponse with items and correct meta" {
            // given
            val events = listOf(
                Event(id = UUID.randomUUID(), date = LocalDate(2026, 1, 1), title = "A", description = ""),
                Event(id = UUID.randomUUID(), date = LocalDate(2026, 2, 1), title = "B", description = "")
            )
            every { repository.findFiltered(null, null, any()) } returns events
            every { repository.countFiltered(null, null) } returns 2L

            // when
            val result = service.listEvents(null, null, limit = 10, offset = 0)

            // then
            result shouldBe PaginatedResponse(
                items = events,
                meta = PaginationMeta(total = 2L, limit = 10, offset = 0)
            )
        }

        "returns empty list with zero total when no events exist" {
            // given
            every { repository.findFiltered(null, null, any()) } returns emptyList()
            every { repository.countFiltered(null, null) } returns 0L

            // when
            val result = service.listEvents(null, null, limit = 50, offset = 0)

            // then
            result.items shouldHaveSize 0
            result.meta.total shouldBe 0L
            result.meta.limit shouldBe 50
            result.meta.offset shouldBe 0
        }
    }

    "listEvents with filters" - {

        "passes fromDate and toDate to repository" {
            // given
            val fromDate = LocalDate(2026, 1, 1)
            val toDate = LocalDate(2026, 12, 31)
            every { repository.findFiltered(fromDate, toDate, any()) } returns emptyList()
            every { repository.countFiltered(fromDate, toDate) } returns 0L

            // when
            service.listEvents(fromDate, toDate, limit = 10, offset = 5)

            // then
            verify(exactly = 1) { repository.findFiltered(fromDate, toDate, any()) }
            verify(exactly = 1) { repository.countFiltered(fromDate, toDate) }
        }
    }

    "updateEvent" - {

        "updates only provided fields" {
            // given
            val eventId = UUID.randomUUID()
            val existing = Event(
                id = eventId,
                date = LocalDate(2026, 1, 1),
                title = "Old Title",
                description = "Old Desc"
            )
            val request = UpdateEventRequest(
                date = null,
                title = "New Title",
                description = null
            )
            every { repository.findById(eventId) } returns Optional.of(existing)
            every { repository.save(any<Event>()) } answers { firstArg() }

            // when
            val result = service.updateEvent(eventId, request)

            // then
            result.id shouldBe eventId
            result.date shouldBe LocalDate(2026, 1, 1) // unchanged
            result.title shouldBe "New Title"  // updated
            result.description shouldBe "Old Desc" // unchanged
            verify(exactly = 1) { notificationClient.send("Событие обновлено", any()) }
        }

        "throws EventNotFoundException when event not found" {
            // given
            val eventId = UUID.randomUUID()
            every { repository.findById(eventId) } returns Optional.empty()

            // when / then
            shouldThrow<EventNotFoundException> {
                service.updateEvent(eventId, UpdateEventRequest(title = "X"))
            }
        }
    }

    "deleteEvent" - {

        "deletes when event exists" {
            // given
            val eventId = UUID.randomUUID()
            every { repository.existsById(eventId) } returns true
            justRun { repository.deleteById(eventId) }

            // when
            service.deleteEvent(eventId)

            // then
            verify(exactly = 1) { repository.existsById(eventId) }
            verify(exactly = 1) { repository.deleteById(eventId) }
            verify(exactly = 1) { notificationClient.send("Событие удалено", any()) }
        }

        "throws EventNotFoundException when event not found" {
            // given
            val eventId = UUID.randomUUID()
            every { repository.existsById(eventId) } returns false

            // when / then
            shouldThrow<EventNotFoundException> {
                service.deleteEvent(eventId)
            }
        }
    }
})
