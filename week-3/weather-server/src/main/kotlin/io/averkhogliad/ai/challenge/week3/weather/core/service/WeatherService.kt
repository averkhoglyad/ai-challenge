package io.averkhogliad.ai.challenge.week3.weather.core.service

import io.averkhogliad.ai.challenge.week3.weather.core.exception.CityNotFoundException
import io.averkhogliad.ai.challenge.week3.weather.core.exception.InvalidParametersException
import io.averkhogliad.ai.challenge.week3.weather.core.exception.ProviderUnavailableException
import io.averkhogliad.ai.challenge.week3.weather.core.geocoding.CityResolver
import io.averkhogliad.ai.challenge.week3.weather.core.model.*
import io.averkhogliad.ai.challenge.week3.weather.core.validation.WeatherInputValidator
import io.averkhogliad.ai.challenge.week3.weather.infra.cache.CacheKeyNormalizer
import io.averkhogliad.ai.challenge.week3.weather.infra.cache.WeatherCache
import io.averkhogliad.ai.challenge.week3.weather.infra.client.DailyData
import io.averkhogliad.ai.challenge.week3.weather.infra.client.OpenMeteoProvider
import io.averkhogliad.ai.challenge.week3.weather.infra.client.UnitConverter
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.time.Clock


private val SERVER_TIME_ZONE: TimeZone = TimeZone.currentSystemDefault()

@Service
class WeatherService(

    private val cityResolver: CityResolver,
    private val openMeteoClient: OpenMeteoProvider,
    private val weatherCache: WeatherCache
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun getCurrentWeather(city: String, country: String?): CurrentWeatherData {
        val cityInfo = cityResolver.resolveCity(city, country)
        val cacheKey = CacheKeyNormalizer.currentWeatherKey(cityInfo.geonameId)

        weatherCache.getCurrent(cacheKey)?.let { cached ->
            if (!cached.stale) {
                logger.debug("Current weather cache HIT for: $city")
                return cached.data
            }
        }
        logger.debug("Current weather cache MISS for: $city")

        return try {
            fetchAndCacheCurrent(cityInfo, cacheKey)
        } catch (e: Exception) {
            val staleData = weatherCache.getCurrent(cacheKey)
            if (staleData != null) {
                logger.warn("[FALLBACK] Returning stale current weather for: $city")
                return staleData.data.copy(stale = true)
            }
            throw when (e) {
                is ProviderUnavailableException -> e
                is CityNotFoundException -> e
                is InvalidParametersException -> e
                else -> ProviderUnavailableException("Failed to fetch current weather: ${e.message}", e)
            }
        }
    }

    private fun fetchAndCacheCurrent(cityInfo: CityInfo, cacheKey: String): CurrentWeatherData {
        val response = openMeteoClient.getCurrentWeather(cityInfo.latitude, cityInfo.longitude)
        val current = response.current
            ?: throw ProviderUnavailableException("No current weather data from provider")

        val result = CurrentWeatherData(
            temperature = current.temperature2m,
            feelsLike = current.apparentTemperature ?: current.temperature2m,
            humidity = current.relativeHumidity2m ?: 0,
            windSpeed = UnitConverter.kmhToMps(current.windSpeed10m),
            windGusts = current.windGusts10m?.let { UnitConverter.kmhToMps(it) },
            pressure = current.surfacePressure,
            weatherCondition = WmoMapper.fromWmoCode(current.weatherCode),
            weatherDescription = WmoMapper.fromWmoCode(current.weatherCode).description,
            observationTime = current.time ?: currentDateTime(),
            city = cityInfo,
            stale = false
        )


        weatherCache.putCurrent(cacheKey, result)
        return result
    }

    fun getForecast(
        city: String,
        country: String?,
        days: Int
    ): ForecastData {
        WeatherInputValidator.validateDays(days)

        val cityInfo = cityResolver.resolveCity(city, country)
        val cacheKey = CacheKeyNormalizer.forecastKey(cityInfo.geonameId, days)

        weatherCache.getForecast(cacheKey)?.let { cached ->
            if (!cached.stale) {
                logger.debug("Forecast cache HIT for: $city ($days days)")
                return cached.data
            }
        }
        logger.debug("Forecast cache MISS for: $city ($days days)")

        return try {
            fetchAndCacheForecast(cityInfo, cacheKey, days)
        } catch (e: Exception) {
            val staleData = weatherCache.getForecast(cacheKey)
            if (staleData != null) {
                logger.warn("[FALLBACK] Returning stale forecast for: $city ($days days)")
                return staleData.data.copy(stale = true)
            }
            throw when (e) {
                is ProviderUnavailableException -> e
                is CityNotFoundException -> e
                is InvalidParametersException -> e
                else -> ProviderUnavailableException("Failed to fetch forecast: ${e.message}", e)
            }
        }
    }

    private fun fetchAndCacheForecast(cityInfo: CityInfo, cacheKey: String, days: Int): ForecastData {
        val response = openMeteoClient.getForecast(cityInfo.latitude, cityInfo.longitude, days)
        val daily = response.daily
            ?: throw ProviderUnavailableException("No forecast data from provider")
        daily.validateRequiredArraySizes()

        val dailyForecasts = daily.time.indices.map { i ->
            val weatherCondition = WmoMapper.fromWmoCode(daily.weatherCode[i])
            DailyForecastData(
                date = daily.time[i],
                weatherCondition = weatherCondition,
                weatherDescription = weatherCondition.description,
                temperatureMax = daily.temperature2mMax[i],
                temperatureMin = daily.temperature2mMin[i],
                precipitationSum = daily.precipitationSum[i],
                windSpeedMax = UnitConverter.kmhToMps(daily.windSpeed10mMax[i])
            )
        }

        val result = ForecastData(
            city = cityInfo,
            days = dailyForecasts,
            generatedAt = currentDateTime().date,
            stale = false
        )

        weatherCache.putForecast(cacheKey, result)

        return result
    }

    private fun currentDateTime() = Clock.System.now().toLocalDateTime(SERVER_TIME_ZONE)


    private fun DailyData.validateRequiredArraySizes() {
        val expectedSize = time.size
        val sizes = mapOf(

            "time" to time.size,
            "weather_code" to weatherCode.size,
            "temperature_2m_max" to temperature2mMax.size,
            "temperature_2m_min" to temperature2mMin.size,
            "precipitation_sum" to precipitationSum.size,
            "wind_speed_10m_max" to windSpeed10mMax.size
        )
        val mismatched = sizes.filterValues { it != expectedSize }
        if (mismatched.isNotEmpty()) {
            throw ProviderUnavailableException(
                "Malformed forecast data from provider: daily array sizes differ ($sizes)"
            )
        }
    }
}
