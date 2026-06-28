package io.averkhogliad.ai.challenge.week3.weather.infra.cache

import com.github.benmanes.caffeine.cache.Cache
import io.averkhogliad.ai.challenge.week3.weather.core.model.CachedWeatherData
import io.averkhogliad.ai.challenge.week3.weather.core.model.CurrentWeatherData
import io.averkhogliad.ai.challenge.week3.weather.core.model.ForecastData
import io.averkhogliad.ai.challenge.week3.weather.infra.config.CurrentWeatherCache
import io.averkhogliad.ai.challenge.week3.weather.infra.config.ForecastWeatherCache
import io.averkhogliad.ai.challenge.week3.weather.infra.config.WeatherDurationProperties
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component


@Component
class CaffeineWeatherCache(
    @CurrentWeatherCache private val currentCache: Cache<String, String>,
    @ForecastWeatherCache private val forecastCache: Cache<String, String>,
    private val json: Json,
    private val durationProperties: WeatherDurationProperties
) : WeatherCache {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun getCurrent(key: String): CachedWeatherData<CurrentWeatherData>? {
        val cached = currentCache.getIfPresent(key) ?: return null
        return try {
            val data = json.decodeFromString<CachedWeatherData<CurrentWeatherData>>(cached)
            val ageMillis = System.currentTimeMillis() - data.cachedAt
            if (ageMillis > durationProperties.currentTtl.inWholeMilliseconds) {
                logger.debug("CACHE STALE: current weather for key: $key")
                data.copy(stale = true)
            } else {
                logger.debug("CACHE HIT: current weather for key: $key")
                data
            }
        } catch (e: Exception) {
            logger.warn("Failed to deserialize cached current weather", e)
            null
        }
    }

    override fun putCurrent(key: String, data: CurrentWeatherData) {
        val entry = CachedWeatherData(data = data, cachedAt = System.currentTimeMillis())
        val serialized = json.encodeToString(
            CachedWeatherData.serializer(CurrentWeatherData.serializer()),
            entry
        )
        currentCache.put(key, serialized)
        logger.debug("Cached current weather for key: $key")
    }

    override fun getForecast(key: String): CachedWeatherData<ForecastData>? {
        val cached = forecastCache.getIfPresent(key) ?: return null
        return try {
            val data = json.decodeFromString<CachedWeatherData<ForecastData>>(cached)
            val ageMillis = System.currentTimeMillis() - data.cachedAt
            if (ageMillis > durationProperties.forecastTtl.inWholeMilliseconds) {
                logger.debug("CACHE STALE: forecast for key: $key")
                data.copy(stale = true)
            } else {
                logger.debug("CACHE HIT: forecast for key: $key")
                data
            }
        } catch (e: Exception) {
            logger.warn("Failed to deserialize cached forecast", e)
            null
        }
    }

    override fun putForecast(key: String, data: ForecastData) {
        val entry = CachedWeatherData(data = data, cachedAt = System.currentTimeMillis())
        val serialized = json.encodeToString(
            CachedWeatherData.serializer(ForecastData.serializer()),
            entry
        )
        forecastCache.put(key, serialized)
        logger.debug("Cached forecast for key: $key")
    }
}
