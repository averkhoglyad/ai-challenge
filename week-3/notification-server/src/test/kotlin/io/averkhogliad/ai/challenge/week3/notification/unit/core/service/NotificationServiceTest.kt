package io.averkhogliad.ai.challenge.week3.notification.unit.core.service

import io.averkhogliad.ai.challenge.week3.notification.core.model.Notification
import io.averkhogliad.ai.challenge.week3.notification.core.repository.NotificationRepository
import io.averkhogliad.ai.challenge.week3.notification.core.service.NotificationService
import io.averkhogliad.ai.challenge.week3.notification.rest.dto.CreateNotificationRequest
import io.averkhogliad.ai.challenge.week3.notification.rest.dto.PaginatedResponse
import io.averkhogliad.ai.challenge.week3.notification.rest.dto.PaginationMeta
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.*

class NotificationServiceTest : FreeSpec({

    lateinit var repository: NotificationRepository
    lateinit var service: NotificationService

    beforeTest {
        repository = mockk()
        service = NotificationService(repository)
    }

    "createNotification" - {

        "saves notification via repository and returns it" {
            // given
            val request = CreateNotificationRequest(
                title = "Alert",
                message = "Something happened"
            )
            every { repository.save(any<Notification>()) } answers { firstArg() }

            // when
            val result = service.createNotification(request)

            // then
            result.title shouldBe request.title
            result.message shouldBe request.message
            verify(exactly = 1) { repository.save(any<Notification>()) }
        }
    }

    "listNotifications" - {

        "returns PaginatedResponse with items and correct meta" {
            // given
            val notifications = listOf(
                Notification(id = UUID.randomUUID(), title = "A", message = "Msg A", createdAt = Instant.now()),
                Notification(id = UUID.randomUUID(), title = "B", message = "Msg B", createdAt = Instant.now())
            )
            every { repository.findAllPaginated(any()) } returns notifications
            every { repository.countAll() } returns 2L

            // when
            val result = service.listNotifications(limit = 10, offset = 0)

            // then
            result shouldBe PaginatedResponse(
                items = notifications,
                meta = PaginationMeta(total = 2L, limit = 10, offset = 0)
            )
        }

        "returns empty list with zero total when no notifications exist" {
            // given
            every { repository.findAllPaginated(any()) } returns emptyList()
            every { repository.countAll() } returns 0L

            // when
            val result = service.listNotifications(limit = 50, offset = 0)

            // then
            result.items shouldHaveSize 0
            result.meta.total shouldBe 0L
            result.meta.limit shouldBe 50
            result.meta.offset shouldBe 0
        }

        "validates limit range" {
            // when / then
            shouldThrow<IllegalArgumentException> {
                service.listNotifications(limit = 0, offset = 0)
            }
            shouldThrow<IllegalArgumentException> {
                service.listNotifications(limit = 101, offset = 0)
            }
        }

        "validates offset is non-negative" {
            // when / then
            shouldThrow<IllegalArgumentException> {
                service.listNotifications(limit = 50, offset = -1)
            }
        }
    }
})
