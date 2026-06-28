package io.averkhogliad.ai.challenge.week3.events.unit.infra.scheduler

import io.averkhogliad.ai.challenge.week3.events.core.model.Event
import io.averkhogliad.ai.challenge.week3.events.core.repository.EventRepository
import io.averkhogliad.ai.challenge.week3.events.core.service.EventService
import io.averkhogliad.ai.challenge.week3.events.infra.client.NotificationClient
import io.averkhogliad.ai.challenge.week3.events.infra.scheduler.DailyEventScheduler
import io.averkhogliad.ai.challenge.week3.events.infra.scheduler.DemoEventScheduler
import io.kotest.core.spec.style.FreeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

class EventSchedulerTest : FreeSpec({

    lateinit var repository: EventRepository
    lateinit var notificationClient: NotificationClient
    lateinit var eventService: EventService

    beforeTest {
        repository = mockk()
        notificationClient = mockk(relaxed = true)
        eventService = EventService(repository, notificationClient, Clock.System)
    }

    "DailyEventScheduler" - {

        "calls eventService.notifyTodayEvents" {
            // given
            val scheduler = DailyEventScheduler(eventService)
            every { repository.findFiltered(any(), any(), any()) } returns emptyList()

            // when
            scheduler.notifyDailyEvents()

            // then
            verify(exactly = 1) { repository.findFiltered(any(), any(), any()) }
        }
    }

    "DemoEventScheduler" - {

        "calls eventService.notifyTodayEvents" {
            // given
            val scheduler = DemoEventScheduler(eventService)
            every { repository.findFiltered(any(), any(), any()) } returns emptyList()

            // when
            scheduler.notifyDemoEvents()

            // then
            verify(exactly = 1) { repository.findFiltered(any(), any(), any()) }
        }
    }

    "notifyTodayEvents" - {

        "sends notification when there are events today" {
            // given
            val fixedClock = object : Clock {
                override fun now(): Instant = Instant.parse("2025-06-15T10:00:00Z")
            }
            val today = fixedClock.todayIn(TimeZone.UTC)
            val svc = EventService(repository, notificationClient, fixedClock)

            val events = listOf(
                Event(id = java.util.UUID.randomUUID(), date = today, title = "Meeting", description = ""),
                Event(id = java.util.UUID.randomUUID(), date = today, title = "Lunch", description = "")
            )
            every { repository.findFiltered(today, today, any()) } returns events

            // when
            svc.notifyTodayEvents()

            // then
            verify(exactly = 1) { notificationClient.send("События на сегодня", any()) }
        }

        "does not send notification when there are no events today" {
            // given
            val fixedClock = object : Clock {
                override fun now(): Instant = Instant.parse("2025-06-15T10:00:00Z")
            }
            val today = fixedClock.todayIn(TimeZone.UTC)
            val svc = EventService(repository, notificationClient, fixedClock)

            every { repository.findFiltered(today, today, any()) } returns emptyList()

            // when
            svc.notifyTodayEvents()

            // then
            verify(exactly = 0) { notificationClient.send("События на сегодня", any()) }
        }
    }
})
