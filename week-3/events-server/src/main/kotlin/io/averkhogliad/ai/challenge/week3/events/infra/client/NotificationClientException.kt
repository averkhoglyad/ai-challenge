package io.averkhogliad.ai.challenge.week3.events.infra.client

class NotificationClientException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
