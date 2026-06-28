package io.averkhogliad.ai.challenge.week3.weather.infra.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

@Configuration
class RestClientConfig(
    private val durationProperties: WeatherDurationProperties
) {
    @Bean
    fun restTemplate(): RestTemplate {
        val restTemplate = RestTemplate()
        // Note: RestTemplate from spring-web does not have direct timeout setters;
        // timeouts are typically configured via the underlying ClientHttpRequestFactory.
        // For production, consider using RestTemplateBuilder with custom request factory.
        return restTemplate
    }
}
