package io.averkhogliad.ai.challenge.week3.events.infra.client

import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange

@HttpExchange("/api/v1/notifications")
interface NotificationApi {

    @PostExchange
    fun send(@RequestBody request: NotificationRequest)
}
