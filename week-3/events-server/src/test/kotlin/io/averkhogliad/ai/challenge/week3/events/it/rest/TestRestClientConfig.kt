package io.averkhogliad.ai.challenge.week3.events.it.rest

import io.averkhogliad.ai.challenge.week3.events.infra.client.NotificationClient
import org.mockito.Mockito
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class TestRestClientConfig {

    @Bean
    @Primary
    fun notificationClient(): NotificationClient = Mockito.mock(NotificationClient::class.java)
}
