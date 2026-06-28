package io.averkhogliad.ai.challenge.week3.weather.infra.client

import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder

private const val MAX_RETRY_ATTEMPTS = 3
private const val INITIAL_RETRY_DELAY_MS = 500L
private const val RETRY_BACKOFF_MULTIPLIER = 2
private const val GEOCODING_RESULTS_COUNT = 10
private const val REQUEST_TIMEZONE = "auto"
private const val GEOCODING_LANGUAGE = "en"
private const val RESPONSE_FORMAT = "json"

@Component
class OpenMeteoClient(
    private val restTemplate: RestTemplate,
    private val json: Json,
    @Value("\${weather.open-meteo.base-url}") private val baseUrl: String,
    @Value("\${weather.open-meteo.geocoding-url}") private val geocodingUrl: String
) : OpenMeteoProvider {
    private val log = LoggerFactory.getLogger(javaClass)

    private fun <T> withRetry(operation: String, block: () -> T): T {
        var attempt = 0
        var delayMs = INITIAL_RETRY_DELAY_MS
        var lastException: Exception? = null

        repeat(MAX_RETRY_ATTEMPTS) {
            try {
                return block()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw e
            } catch (e: Exception) {
                lastException = e
                attempt++
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    log.warn("[RETRY] $operation attempt $attempt failed, retrying in ${delayMs}ms: ${e.message}")
                    try {
                        Thread.sleep(delayMs)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw ie
                    }
                    delayMs *= RETRY_BACKOFF_MULTIPLIER
                }
            }
        }

        throw lastException!!
    }

    override fun geocode(city: String, country: String?): GeocodingResponse {
        log.debug("[OPEN-METEO] Geocoding request: city={}, country={}", city, country)
        val response: GeocodingResponse = withRetry("geocode($city)") {
            val uri = UriComponentsBuilder
                .fromUriString("$geocodingUrl/v1/search")
                .queryParam("name", city)
                .queryParam("count", GEOCODING_RESULTS_COUNT)
                .queryParam("language", GEOCODING_LANGUAGE)
                .queryParam("format", RESPONSE_FORMAT)
                .build()
                .toUri()
            val body = restTemplate.getForObject(uri, String::class.java)
            json.decodeFromString<GeocodingResponse>(body!!)
        }
        log.debug("[OPEN-METEO] Geocoding response: results count={}", response.results?.size ?: 0)
        return response
    }

    override fun getCurrentWeather(lat: Double, lon: Double): OpenMeteoCurrentResponse {
        log.debug("[OPEN-METEO] Current weather request: lat={}, lon={}", lat, lon)
        val response: OpenMeteoCurrentResponse = withRetry("getCurrentWeather($lat,$lon)") {
            val uri = UriComponentsBuilder
                .fromUriString("$baseUrl/v1/forecast")
                .queryParam("latitude", lat)
                .queryParam("longitude", lon)
                .queryParam(
                    "current",
                    "temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,wind_gusts_10m,surface_pressure,precipitation,precipitation_probability,wind_direction_10m"
                )
                .queryParam("timezone", REQUEST_TIMEZONE)
                .build()
                .toUri()
            val body = restTemplate.getForObject(uri, String::class.java)
            json.decodeFromString<OpenMeteoCurrentResponse>(body!!)
        }
        log.debug("[OPEN-METEO] Current weather response: temp={}°C", response.current?.temperature2m)
        return response
    }

    override fun getForecast(lat: Double, lon: Double, days: Int): OpenMeteoForecastResponse {
        log.debug("[OPEN-METEO] Forecast request: lat={}, lon={}, days={}", lat, lon, days)
        val response: OpenMeteoForecastResponse = withRetry("getForecast($lat,$lon,$days)") {
            val uri = UriComponentsBuilder
                .fromUriString("$baseUrl/v1/forecast")
                .queryParam("latitude", lat)
                .queryParam("longitude", lon)
                .queryParam(
                    "daily",
                    "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max,wind_speed_10m_max,wind_direction_10m_dominant,surface_pressure_max"
                )
                .queryParam("forecast_days", days)
                .queryParam("timezone", REQUEST_TIMEZONE)
                .build()
                .toUri()
            val body = restTemplate.getForObject(uri, String::class.java)
            json.decodeFromString<OpenMeteoForecastResponse>(body!!)
        }
        log.debug("[OPEN-METEO] Forecast response: days count={}", response.daily?.time?.size ?: 0)
        return response
    }
}
