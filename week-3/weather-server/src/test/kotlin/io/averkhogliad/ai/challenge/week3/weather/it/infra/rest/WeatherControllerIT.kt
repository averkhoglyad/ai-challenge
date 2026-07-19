package io.averkhogliad.ai.challenge.week3.weather.it.infra.rest

import com.github.benmanes.caffeine.cache.Cache
import io.averkhogliad.ai.challenge.week3.weather.core.exception.ProviderUnavailableException
import io.averkhogliad.ai.challenge.week3.weather.core.model.*
import io.averkhogliad.ai.challenge.week3.weather.core.repository.GeoCacheTable
import io.averkhogliad.ai.challenge.week3.weather.infra.cache.CacheKeyNormalizer
import io.averkhogliad.ai.challenge.week3.weather.infra.client.*
import io.averkhogliad.ai.challenge.week3.weather.infra.config.CurrentWeatherCache
import io.averkhogliad.ai.challenge.week3.weather.infra.config.ForecastWeatherCache
import io.averkhogliad.ai.challenge.week3.weather.it.IntegrationTest
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.FreeSpec
import io.kotest.extensions.spring.SpringExtension
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.test.assertEquals


@IntegrationTest
@ApplyExtension(SpringExtension::class)
class WeatherControllerIT : FreeSpec() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var database: Database

    @Autowired
    @CurrentWeatherCache
    lateinit var currentWeatherCache: Cache<String, String>

    @Autowired
    @ForecastWeatherCache
    lateinit var forecastCache: Cache<String, String>

    @Autowired
    lateinit var json: Json

    @Value("\${weather.database.url}")
    lateinit var databaseUrl: String

    override suspend fun beforeTest(testCase: io.kotest.core.test.TestCase) {
        cleanupState()
    }

    override suspend fun afterSpec(spec: io.kotest.core.spec.Spec) {
        cleanupState()
        deleteDatabaseFile()
    }

    init {
        "GET /api/v1/weather/current" - {
            "returns 200 with valid weather JSON for a valid city" {
                mockMvc.perform(get("/api/v1/weather/current").param("city", "London"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.city.name").value("London"))
                    .andExpect(jsonPath("$.city.country").value("United Kingdom"))
                    .andExpect(jsonPath("$.city.countryCode").value("GB"))
                    .andExpect(jsonPath("$.city.geonameId").value(2643743))
                    .andExpect(jsonPath("$.temperature").value(15.0))
                    .andExpect(jsonPath("$.feelsLike").value(13.0))
                    .andExpect(jsonPath("$.humidity").value(72))
                    .andExpect(jsonPath("$.pressure").value(1012.0))
                    .andExpect(jsonPath("$.weatherCondition").value("PARTLY_CLOUDY"))
                    .andExpect(jsonPath("$.weatherDescription").value("Partly cloudy"))
                    .andExpect(jsonPath("$.windSpeed").value(4.5))
                    .andExpect(jsonPath("$.windGusts").value(6.0))
                    .andExpect(jsonPath("$.observationTime").value("2026-01-01T12:00"))
                    .andExpect(jsonPath("$.stale").value(false))

                assertEquals(1, geoCacheRows())
            }

            "returns stale header and body flag for stale current weather" {
                seedStaleCurrentWeather()

                mockMvc.perform(get("/api/v1/weather/current").param("city", "StaleCurrent"))
                    .andExpect(status().isOk)
                    .andExpect(header().string("X-Stale", "true"))
                    .andExpect(jsonPath("$.city.name").value("StaleCurrent"))
                    .andExpect(jsonPath("$.temperature").value(11.0))
                    .andExpect(jsonPath("$.stale").value(true))
            }

            "returns 400 when city parameter is missing" {
                mockMvc.perform(get("/api/v1/weather/current"))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.details.parameter").value("city"))
            }

            "returns 404 for non-existent city" {
                mockMvc.perform(get("/api/v1/weather/current").param("city", "Atlantis"))
                    .andExpect(status().isNotFound)
                    .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.error.details.city").value("Atlantis"))
            }
        }

        "GET /api/v1/weather/forecast" - {
            "returns 200 with forecast JSON for valid parameters" {
                mockMvc.perform(
                    get("/api/v1/weather/forecast")
                        .param("city", "Paris")
                        .param("country", "FR")
                        .param("days", "5")
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.city.name").value("Paris"))
                    .andExpect(jsonPath("$.city.country").value("France"))
                    .andExpect(jsonPath("$.city.countryCode").value("FR"))
                    .andExpect(jsonPath("$.city.geonameId").value(2988507))
                    .andExpect(jsonPath("$.days.length()").value(5))
                    .andExpect(jsonPath("$.days[0].date").value("2026-01-01"))
                    .andExpect(jsonPath("$.days[0].weatherCondition").value("RAIN"))
                    .andExpect(jsonPath("$.days[0].weatherDescription").value("Rain"))
                    .andExpect(jsonPath("$.days[0].temperatureMax").value(12.0))
                    .andExpect(jsonPath("$.days[0].temperatureMin").value(7.0))
                    .andExpect(jsonPath("$.days[0].precipitationSum").value(5.0))
                    .andExpect(jsonPath("$.days[0].windSpeedMax").value(10.0))
                    .andExpect(jsonPath("$.days[1].weatherCondition").value("CLEAR_SKY"))
                    .andExpect(jsonPath("$.generatedAt").exists())
                    .andExpect(jsonPath("$.stale").value(false))
            }

            "returns stale header and body flag for stale forecast" {
                seedStaleForecast()

                mockMvc.perform(
                    get("/api/v1/weather/forecast")
                        .param("city", "StaleForecast")
                        .param("country", "SF")
                        .param("days", "5")
                )
                    .andExpect(status().isOk)
                    .andExpect(header().string("X-Stale", "true"))
                    .andExpect(jsonPath("$.city.name").value("StaleForecast"))
                    .andExpect(jsonPath("$.days.length()").value(1))
                    .andExpect(jsonPath("$.stale").value(true))
            }

            "returns 400 when days > 14" {
                mockMvc.perform(get("/api/v1/weather/forecast").param("city", "Paris").param("days", "15"))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.details.parameter").value("days"))
            }

            "returns 400 when days is not an integer" {
                mockMvc.perform(get("/api/v1/weather/forecast").param("city", "Paris").param("days", "abc"))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.details.parameter").value("days"))
            }

            "returns 400 when days < 1" {
                mockMvc.perform(get("/api/v1/weather/forecast").param("city", "Paris").param("days", "0"))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.details.parameter").value("days"))
            }

            "returns 400 when city parameter is missing" {
                mockMvc.perform(get("/api/v1/weather/forecast"))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.details.parameter").value("city"))
            }
        }
    }

    private fun cleanupState() {
        currentWeatherCache.invalidateAll()
        forecastCache.invalidateAll()
        transaction(database) {
            SchemaUtils.create(GeoCacheTable)
            GeoCacheTable.deleteAll()
        }
    }

    private fun geoCacheRows(): Long = transaction(database) {
        GeoCacheTable.selectAll().count()
    }

    private fun seedStaleCurrentWeather() {
        val city = staleCity("StaleCurrent", "SC", 111111)
        val data = CurrentWeatherData(
            temperature = 11.0,
            feelsLike = 10.0,
            humidity = 70,
            windSpeed = 1.0,
            windGusts = null,
            pressure = 1000.0,
            weatherCondition = WeatherCondition.CLEAR_SKY,
            weatherDescription = "Clear sky",
            observationTime = LocalDateTime.parse("2026-01-01T00:00"),
            city = city,
            stale = false
        )
        currentWeatherCache.put(
            CacheKeyNormalizer.currentWeatherKey(city.geonameId),
            json.encodeToString(CachedWeatherData.serializer(CurrentWeatherData.serializer()), staleEntry(data))
        )
    }

    private fun seedStaleForecast() {
        val city = staleCity("StaleForecast", "SF", 222222)
        val data = ForecastData(
            city = city,
            days = listOf(
                DailyForecastData(
                    date = LocalDate.parse("2026-01-01"),
                    weatherCondition = WeatherCondition.RAIN,
                    weatherDescription = "Rain",
                    temperatureMax = 10.0,
                    temperatureMin = 5.0,
                    precipitationSum = 1.0,
                    windSpeedMax = 2.0
                )
            ),
            generatedAt = LocalDate.parse("2026-01-01"),
            stale = false
        )
        forecastCache.put(
            CacheKeyNormalizer.forecastKey(city.geonameId, 5),
            json.encodeToString(CachedWeatherData.serializer(ForecastData.serializer()), staleEntry(data))
        )
    }

    private fun staleCity(name: String, countryCode: String, geonameId: Long) = CityInfo(
        name = name,
        country = "Stale Country",
        countryCode = countryCode,
        latitude = 0.0,
        longitude = 0.0,
        geonameId = geonameId
    )

    private fun <T> staleEntry(data: T) = CachedWeatherData(
        data = data,
        cachedAt = 0L,
        stale = false
    )

    private fun deleteDatabaseFile() {
        val path = databaseUrl.removePrefix("jdbc:sqlite:")
        if (path.isNotBlank()) {
            Path.of(path).deleteIfExists()
        }
    }

    @TestConfiguration
    class TestConfig {
        @Bean
        @Primary
        fun openMeteoProvider(): OpenMeteoProvider = FakeOpenMeteoProvider()
    }

    private class FakeOpenMeteoProvider : OpenMeteoProvider {
        override fun geocode(city: String, country: String?): GeocodingResponse = when (city) {
            "London" -> GeocodingResponse(listOf(london))
            "Paris" -> GeocodingResponse(listOf(paris))
            "Atlantis" -> GeocodingResponse(emptyList())
            "StaleCurrent" -> GeocodingResponse(listOf(staleCurrent))
            "StaleForecast" -> GeocodingResponse(listOf(staleForecast))
            else -> error("Unexpected geocoding request in REST IT: city=$city country=$country")
        }

        override fun getCurrentWeather(lat: Double, lon: Double): OpenMeteoCurrentResponse {
            if (lat == 0.0 && lon == 0.0) {
                throw ProviderUnavailableException("forced current provider failure for stale fallback")
            }
            if (lat != 51.51 || lon != -0.13) {
                error("Unexpected current weather request in REST IT: lat=$lat lon=$lon")
            }
            return OpenMeteoCurrentResponse(
                current = CurrentData(
                    time = LocalDateTime.parse("2026-01-01T12:00"),
                    temperature2m = 15.0,
                    relativeHumidity2m = 72,
                    apparentTemperature = 13.0,
                    weatherCode = 2,
                    windSpeed10m = 16.2,
                    windGusts10m = 21.6,
                    surfacePressure = 1012.0,
                    precipitation = 0.0,
                    precipitationProbability = 10,
                    windDirection10m = 240
                ),
                timezone = "Europe/London",
                timezoneAbbreviation = "GMT"
            )
        }

        override fun getForecast(lat: Double, lon: Double, days: Int): OpenMeteoForecastResponse {
            if (lat == 0.0 && lon == 0.0) {
                throw ProviderUnavailableException("forced forecast provider failure for stale fallback")
            }
            if (lat != 48.8566 || lon != 2.3522) {
                error("Unexpected forecast request in REST IT: lat=$lat lon=$lon days=$days")
            }
            return OpenMeteoForecastResponse(
                daily = DailyData(
                    time = (1..days).map { day -> LocalDate.parse("2026-01-${day.toString().padStart(2, '0')}") },
                    weatherCode = listOf(61, 0, 2, 3, 45, 51, 71, 95, 80, 65, 1, 63, 73, 77).take(days),
                    temperature2mMax = List(days) { 12.0 + it },
                    temperature2mMin = List(days) { 7.0 + it },
                    precipitationSum = List(days) { if (it == 0) 5.0 else 0.0 },
                    precipitationProbabilityMax = List(days) { 80 },
                    windSpeed10mMax = List(days) { if (it == 0) 36.0 else 10.8 },
                    windDirection10mDominant = List(days) { 180 },
                    surfacePressureMax = List(days) { 1010.0 }
                ),
                timezone = "Europe/Paris",
                timezoneAbbreviation = "CET"
            )
        }

        companion object {
            private val london = GeoResult(
                id = 2643743,
                name = "London",
                latitude = 51.51,
                longitude = -0.13,
                country = "United Kingdom",
                countryCode = "GB",
                admin1 = "England",
                population = 8961989
            )
            private val paris = GeoResult(
                id = 2988507,
                name = "Paris",
                latitude = 48.8566,
                longitude = 2.3522,
                country = "France",
                countryCode = "FR",
                admin1 = "Île-de-France",
                population = 2161000
            )
            private val staleCurrent = GeoResult(111111, "StaleCurrent", 0.0, 0.0, "Stale Country", countryCode = "SC")
            private val staleForecast =
                GeoResult(222222, "StaleForecast", 0.0, 0.0, "Stale Country", countryCode = "SF")
        }
    }
}
