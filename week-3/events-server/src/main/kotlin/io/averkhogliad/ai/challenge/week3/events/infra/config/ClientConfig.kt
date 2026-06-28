package io.averkhogliad.ai.challenge.week3.events.infra.config

import io.averkhogliad.ai.challenge.week3.events.infra.client.NotificationApi
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory

@Configuration
class ClientConfig {

    @Bean
    fun notificationApi(@Value("\${app.notification-server.url}") baseUrl: String): NotificationApi {
        val restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .build()
        return HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
            .createClient(NotificationApi::class.java)
    }
}
