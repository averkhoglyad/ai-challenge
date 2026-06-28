package io.averkhogliad.ai.challenge.week3.events.infra.client

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException

@Service
class NotificationClient(
    private val notificationApi: NotificationApi
) {

    private val logger = LoggerFactory.getLogger(NotificationClient::class.java)

    fun send(title: String, message: String) {
        val request = NotificationRequest(title = title, message = message)
        try {
            logger.info("Sending notification: title={}", title)
            notificationApi.send(request)
            logger.info("Notification sent successfully: title={}", title)
        } catch (e: HttpStatusCodeException) {
            logger.error("Notification server returned error status: {}", e.statusCode)
            throw NotificationClientException(
                "Notification server returned error status: ${e.statusCode}"
            )
        } catch (e: Exception) {
            logger.error("Failed to send notification: title={}, error={}", title, e.message, e)
            throw NotificationClientException("Failed to send notification: ${e.message}", e)
        }
    }
}
