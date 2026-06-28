package io.averkhogliad.ai.challenge.week3.weather.unit.cache

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.averkhogliad.ai.challenge.week3.weather.core.model.*
import io.averkhogliad.ai.challenge.week3.weather.infra.cache.CaffeineWeatherCache
import io.averkhogliad.ai.challenge.week3.weather.infra.config.StringToDurationConverter
import io.averkhogliad.ai.challenge.week3.weather.infra.config.WeatherDurationProperties
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import org.springframework.core.convert.support.DefaultConversionService


class CaffeineWeatherCacheTest : FreeSpec({

    lateinit var currentCache: Cache<String, String>
    lateinit var forecastCache: Cache<String, String>
    lateinit var json: Json
    lateinit var weatherCache: CaffeineWeatherCache

    val testCity = CityInfo(
        name = "TestCity",
        country = "TestCountry",
        countryCode = "TC",
        latitude = 0.0,
        longitude = 0.0,
        geonameId = 1
    )

    val testCurrentWeather = CurrentWeatherData(
        temperature = 22.5,
        feelsLike = 21.0,
        humidity = 65,
        windSpeed = 5.0,
        windGusts = 7.5,
        pressure = 1013.0,
        weatherCondition = WeatherCondition.PARTLY_CLOUDY,
        weatherDescription = "Partly cloudy",
        observationTime = LocalDateTime.parse("2026-01-01T12:00"),
        city = testCity
    )

    val testForecast = ForecastData(
        city = testCity,
        days = listOf(
            DailyForecastData(
                date = LocalDate.parse("2026-01-01"),
                weatherCondition = WeatherCondition.CLEAR_SKY,
                weatherDescription = "Clear sky",
                temperatureMax = 25.0,
                temperatureMin = 15.0,
                precipitationSum = 0.0,
                windSpeedMax = 3.0
            )
        ),
        generatedAt = LocalDate.parse("2026-01-01")
    )

    beforeEach {
        currentCache = Caffeine.newBuilder().maximumSize(100).build()
        forecastCache = Caffeine.newBuilder().maximumSize(100).build()
        json = Json { ignoreUnknownKeys = true }
        val conversionService = DefaultConversionService().apply {
            addConverter(StringToDurationConverter())
        }
        weatherCache = CaffeineWeatherCache(
            currentCache,
            forecastCache,
            json,
            durationProperties = WeatherDurationProperties(
                conversionService = conversionService,
                connectTimeout = "PT3S",
                readTimeout = "PT5S",
                geoTtl = "P30D",
                currentTtl = "PT15M",
                forecastTtl = "PT3H"
            )
        )
    }

    "current weather cache" - {
        "stores and retrieves current weather data" {
            val key = "current:1"

            weatherCache.putCurrent(key, testCurrentWeather)
            val result = weatherCache.getCurrent(key)

            result shouldNotBe null
            result!!.data.temperature shouldBe 22.5
            result.data.humidity shouldBe 65
            result.data.weatherCondition shouldBe WeatherCondition.PARTLY_CLOUDY
            result.stale shouldBe false
        }

        "returns null when no data cached" {
            weatherCache.getCurrent("current:nonexistent").shouldBeNull()
        }

        "returns null for corrupted data" {
            currentCache.put("current:corrupt", "this is not valid json")

            weatherCache.getCurrent("current:corrupt").shouldBeNull()
        }
    }

    "forecast cache" - {
        "stores and retrieves forecast data" {
            val key = "forecast:1:7"

            weatherCache.putForecast(key, testForecast)
            val result = weatherCache.getForecast(key)

            result shouldNotBe null
            result!!.data.city.name shouldBe "TestCity"
            result.data.days shouldHaveSize 1
            result.data.days[0].date shouldBe LocalDate.parse("2026-01-01")
            result.data.days[0].weatherCondition shouldBe WeatherCondition.CLEAR_SKY
        }

        "returns null when no data cached" {
            weatherCache.getForecast("forecast:nonexistent").shouldBeNull()
        }

        "returns null for corrupted data" {
            forecastCache.put("forecast:corrupt", "corrupted json {{{")

            weatherCache.getForecast("forecast:corrupt").shouldBeNull()
        }
    }
})
