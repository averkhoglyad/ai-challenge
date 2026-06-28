package io.averkhogliad.ai.challenge.week3.weather.infra.config

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

private const val CURRENT_WEATHER_CACHE_SIZE = 10_000L
private const val FORECAST_CACHE_SIZE = 5_000L

@Configuration
class CacheConfig {
    @Bean
    fun currentWeatherCache(): Cache<String, String> {
        return Caffeine.newBuilder()
            .maximumSize(CURRENT_WEATHER_CACHE_SIZE)
            .build()
    }

    @Bean
    fun forecastCache(): Cache<String, String> {
        return Caffeine.newBuilder()
            .maximumSize(FORECAST_CACHE_SIZE)
            .build()
    }
}
