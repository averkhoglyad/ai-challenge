package io.averkhogliad.ai.challenge.week3.weather.infra.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.convert.ConversionService
import org.springframework.stereotype.Component
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

private val DEFAULT_GEO_TTL: Duration = 30.days

@Component
class WeatherDurationProperties(
    conversionService: ConversionService,
    @Value("\${weather.open-meteo.connect-timeout}") connectTimeout: String,
    @Value("\${weather.open-meteo.read-timeout}") readTimeout: String,
    @Value("\${weather.cache.geo-ttl}") geoTtl: String,
    @Value("\${weather.cache.current-ttl}") currentTtl: String,
    @Value("\${weather.cache.forecast-ttl}") forecastTtl: String
) {
    val connectTimeout: Duration = conversionService.convert(connectTimeout, Duration::class.java)
        ?: error("Unable to convert weather.open-meteo.connect-timeout to Kotlin Duration")
    val readTimeout: Duration = conversionService.convert(readTimeout, Duration::class.java)
        ?: error("Unable to convert weather.open-meteo.read-timeout to Kotlin Duration")
    val geoTtl: Duration = conversionService.convert(geoTtl, Duration::class.java) ?: DEFAULT_GEO_TTL
    val currentTtl: Duration = conversionService.convert(currentTtl, Duration::class.java)
        ?: error("Unable to convert weather.cache.current-ttl to Kotlin Duration")
    val forecastTtl: Duration = conversionService.convert(forecastTtl, Duration::class.java)
        ?: error("Unable to convert weather.cache.forecast-ttl to Kotlin Duration")
}
