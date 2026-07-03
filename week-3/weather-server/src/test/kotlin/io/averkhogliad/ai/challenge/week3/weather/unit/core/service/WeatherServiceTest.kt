package io.averkhogliad.ai.challenge.week3.weather.unit.core.service

import io.averkhogliad.ai.challenge.week3.weather.core.exception.ProviderUnavailableException
import io.averkhogliad.ai.challenge.week3.weather.core.geocoding.CityResolver
import io.averkhogliad.ai.challenge.week3.weather.core.model.CityInfo
import io.averkhogliad.ai.challenge.week3.weather.core.service.WeatherService
import io.averkhogliad.ai.challenge.week3.weather.infra.cache.WeatherCache
import io.averkhogliad.ai.challenge.week3.weather.infra.client.DailyData
import io.averkhogliad.ai.challenge.week3.weather.infra.client.OpenMeteoClient
import io.averkhogliad.ai.challenge.week3.weather.infra.client.OpenMeteoForecastResponse
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.LocalDate

class WeatherServiceTest : FreeSpec({

    "getForecast" - {
        "throws ProviderUnavailableException for malformed daily array sizes" {
            val city = CityInfo(
                name = "London",
                country = "United Kingdom",
                countryCode = "GB",
                latitude = 51.51,
                longitude = -0.13,
                geonameId = 2643743
            )
            val cityResolver = mockk<CityResolver>()
            val openMeteoClient = mockk<OpenMeteoClient>()
            val weatherCache = mockk<WeatherCache>()
            val service = WeatherService(cityResolver, openMeteoClient, weatherCache)

            every { cityResolver.resolveCity("London", null) } returns city
            every { weatherCache.getForecast("forecast:2643743:3") } returns null
            every { openMeteoClient.getForecast(51.51, -0.13, 3) } returns OpenMeteoForecastResponse(
                daily = DailyData(
                    time = listOf(
                        LocalDate.parse("2026-01-01"),
                        LocalDate.parse("2026-01-02"),
                        LocalDate.parse("2026-01-03")
                    ),
                    weatherCode = listOf(1, 2),
                    temperature2mMax = listOf(20.0, 21.0, 22.0),
                    temperature2mMin = listOf(10.0, 11.0, 12.0),
                    precipitationSum = listOf(0.0, 0.0, 0.0),
                    windSpeed10mMax = listOf(5.0, 6.0, 7.0)
                )
            )

            val ex = shouldThrow<ProviderUnavailableException> {
                service.getForecast("London", null, 3)
            }
            ex.message shouldContain "Malformed forecast data from provider"
        }
    }
})
