package io.averkhogliad.ai.challenge.week3.weather.unit.infra.client

import io.averkhogliad.ai.challenge.week3.weather.infra.client.GeocodingResponse
import io.averkhogliad.ai.challenge.week3.weather.infra.client.OpenMeteoCurrentResponse
import io.averkhogliad.ai.challenge.week3.weather.infra.client.OpenMeteoForecastResponse
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json

class OpenMeteoClientTest : FreeSpec({

    val json = Json { ignoreUnknownKeys = true }

    "geocode" - {
        "parses GeocodingResponse correctly" {
            val result = json.decodeFromString<GeocodingResponse>(GEOCODING_RESPONSE_JSON)

            result.results shouldNotBe null
            result.results!!.size shouldBe 2
            result.results!![0].name shouldBe "London"
            result.results!![0].countryCode shouldBe "GB"
            result.results!![0].id shouldBe 2643743
        }
    }

    "getCurrentWeather" - {
        "parses OpenMeteoCurrentResponse correctly" {
            val result = json.decodeFromString<OpenMeteoCurrentResponse>(CURRENT_WEATHER_RESPONSE_JSON)

            result.current shouldNotBe null
            result.current!!.temperature2m shouldBe 15.3
            result.current!!.weatherCode shouldBe 2
            result.current!!.windSpeed10m shouldBe 18.0
        }
    }

    "getForecast" - {
        "parses OpenMeteoForecastResponse correctly" {
            val result = json.decodeFromString<OpenMeteoForecastResponse>(FORECAST_RESPONSE_JSON)

            result.daily shouldNotBe null
            result.daily!!.time.size shouldBe 3
            result.daily!!.temperature2mMax[0] shouldBe 20.0
            result.daily!!.weatherCode[1] shouldBe 3
        }
    }
}) {
    companion object {
        val GEOCODING_RESPONSE_JSON = """
            {"results":[{"id":2643743,"name":"London","latitude":51.50853,"longitude":-0.12574,"country":"United Kingdom","country_code":"GB"},{"id":6058560,"name":"London","latitude":42.98339,"longitude":-81.23304,"country":"Canada","country_code":"CA"}]}
        """.trimIndent()

        val CURRENT_WEATHER_RESPONSE_JSON = """
            {"current":{"temperature_2m":15.3,"relative_humidity_2m":72,"weather_code":2,"wind_speed_10m":18.0,"surface_pressure":1013.0,"time":"2026-01-01T12:00"},"timezone":"Europe/London"}
        """.trimIndent()

        val FORECAST_RESPONSE_JSON = """
            {"daily":{"time":["2026-01-01","2026-01-02","2026-01-03"],"weather_code":[2,3,1],"temperature_2m_max":[20.0,18.0,22.0],"temperature_2m_min":[10.0,8.0,12.0],"precipitation_sum":[0.0,2.5,0.0],"wind_speed_10m_max":[15.0,20.0,10.0]},"timezone":"Europe/London"}
        """.trimIndent()
    }
}
