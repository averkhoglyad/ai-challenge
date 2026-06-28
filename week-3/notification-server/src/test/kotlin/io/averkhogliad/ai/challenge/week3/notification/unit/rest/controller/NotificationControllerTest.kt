package io.averkhogliad.ai.challenge.week3.notification.unit.rest.controller

import io.averkhogliad.ai.challenge.week3.notification.core.model.Notification
import io.averkhogliad.ai.challenge.week3.notification.core.service.NotificationService
import io.averkhogliad.ai.challenge.week3.notification.rest.controller.NotificationController
import io.averkhogliad.ai.challenge.week3.notification.rest.dto.CreateNotificationRequest
import io.averkhogliad.ai.challenge.week3.notification.rest.dto.PaginatedResponse
import io.averkhogliad.ai.challenge.week3.notification.rest.dto.PaginationMeta
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.*

class NotificationControllerTest : FreeSpec({

    lateinit var service: NotificationService
    lateinit var controller: NotificationController

    beforeTest {
        service = mockk()
        controller = NotificationController(service)
    }

    "POST /api/v1/notifications" - {

        "returns 201 with created notification" {
            // given
            val request = CreateNotificationRequest(
                title = "Test",
                message = "Hello"
            )
            val notification = Notification(
                id = UUID.randomUUID(),
                title = "Test",
                message = "Hello",
                createdAt = Instant.now()
            )
            every { service.createNotification(request) } returns notification

            // when
            val response = controller.createNotification(request)

            // then
            response.statusCode shouldBe HttpStatus.CREATED
            response.body shouldBe notification
            verify(exactly = 1) { service.createNotification(request) }
        }
    }

    "GET /api/v1/notifications" - {

        "returns paginated response with default params" {
            // given
            val paginatedResponse = PaginatedResponse(
                items = emptyList(),
                meta = PaginationMeta(total = 0, limit = 50, offset = 0)
            )
            every { service.listNotifications(50, 0) } returns paginatedResponse

            // when
            val response = controller.listNotifications(50, 0)

            // then
            response shouldBe paginatedResponse
            verify(exactly = 1) { service.listNotifications(50, 0) }
        }

        "passes custom limit and offset to service" {
            // given
            val paginatedResponse = PaginatedResponse(
                items = emptyList(),
                meta = PaginationMeta(total = 0, limit = 10, offset = 20)
            )
            every { service.listNotifications(10, 20) } returns paginatedResponse

            // when
            val response = controller.listNotifications(10, 20)

            // then
            response shouldBe paginatedResponse
            verify(exactly = 1) { service.listNotifications(10, 20) }
        }
    }
})
