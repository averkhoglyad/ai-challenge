package io.averkhogliad.ai.challenge.week3.events.infra.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import org.springframework.http.HttpStatusCode
import org.springframework.web.client.HttpStatusCodeException

class NotificationClientTest : FreeSpec({

    "NotificationClient" - {

        "send should successfully post notification" {
            // given
            val mockApi = mockk<NotificationApi> {
                every { send(any()) } just runs
            }
            val client = NotificationClient(mockApi)

            // when
            client.send("Test Title", "Test Message")

            // then
            verify(exactly = 1) { mockApi.send(NotificationRequest("Test Title", "Test Message")) }
        }

        "send should throw NotificationClientException on network error" {
            // given
            val causeException = java.io.IOException("Connection refused")
            val mockApi = mockk<NotificationApi> {
                every { send(any()) } throws causeException
            }
            val client = NotificationClient(mockApi)

            // when / then
            val exception = shouldThrow<NotificationClientException> {
                client.send("Test Title", "Test Message")
            }
            exception.message shouldContain "Failed to send notification"
        }

        "send should throw NotificationClientException on server error response" {
            // given
            val statusException = object : HttpStatusCodeException(
                HttpStatusCode.valueOf(500),
                "Internal Server Error"
            ) {}
            val mockApi = mockk<NotificationApi> {
                every { send(any()) } throws statusException
            }
            val client = NotificationClient(mockApi)

            // when / then
            val exception = shouldThrow<NotificationClientException> {
                client.send("Test Title", "Test Message")
            }
            exception.message shouldContain "Notification server returned error status"
        }
    }
})
