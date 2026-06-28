package io.averkhogliad.ai.challenge.week3.weather.unit.service

import io.averkhogliad.ai.challenge.week3.weather.core.exception.CityNotFoundException
import io.averkhogliad.ai.challenge.week3.weather.core.model.GeoCacheEntry
import io.averkhogliad.ai.challenge.week3.weather.core.repository.GeoCacheRepository
import io.averkhogliad.ai.challenge.week3.weather.core.service.GeocodingService
import io.averkhogliad.ai.challenge.week3.weather.infra.client.GeoResult
import io.averkhogliad.ai.challenge.week3.weather.infra.client.GeocodingResponse
import io.averkhogliad.ai.challenge.week3.weather.infra.client.OpenMeteoClient
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class GeocodingServiceTest : FreeSpec({

    lateinit var openMeteoClient: OpenMeteoClient
    lateinit var geoCacheRepository: GeoCacheRepository
    lateinit var geocodingService: GeocodingService

    beforeEach {
        openMeteoClient = mockk()
        geoCacheRepository = mockk()
        geocodingService = GeocodingService(openMeteoClient, geoCacheRepository)
    }

    "resolveCity" - {
        "returns CityInfo from cache when cache hit" {
            val cachedEntry = GeoCacheEntry(
                name = "London",
                country = "United Kingdom",
                countryCode = "GB",
                latitude = 51.51,
                longitude = -0.13,
                geonameId = 2643743,
                population = 8_000_000,
                alternativesCount = 29
            )
            every { geoCacheRepository.findByKey("geo:london") } returns cachedEntry

            val result = geocodingService.resolveCity("London", null)

            result.name shouldBe "London"
            result.country shouldBe "United Kingdom"
            result.countryCode shouldBe "GB"
            result.geonameId shouldBe 2643743
            verify(exactly = 0) { openMeteoClient.geocode(any(), any()) }
        }

        "fetches from Open-Meteo and saves to cache on cache miss" {
            every { geoCacheRepository.findByKey("geo:paris") } returns null
            val geoResult = GeoResult(
                id = 2988507,
                name = "Paris",
                latitude = 48.85,
                longitude = 2.35,
                country = "France",
                countryCode = "FR",
                population = 2_000_000
            )
            every { openMeteoClient.geocode("Paris", null) } returns GeocodingResponse(results = listOf(geoResult))
            every { geoCacheRepository.save(any(), any()) } returns Unit

            val result = geocodingService.resolveCity("Paris", null)

            result.name shouldBe "Paris"
            result.country shouldBe "France"
            result.geonameId shouldBe 2988507
            verify(exactly = 1) { openMeteoClient.geocode("Paris", null) }
            verify(exactly = 1) { geoCacheRepository.save("geo:paris", any()) }
        }

        "throws CityNotFoundException when no results from Open-Meteo" {
            every { geoCacheRepository.findByKey("geo:unknown") } returns null
            every { openMeteoClient.geocode("Unknown", null) } returns GeocodingResponse(results = emptyList())

            shouldThrow<CityNotFoundException> {
                geocodingService.resolveCity("Unknown", null)
            }
        }

        "filters by country code when country is provided" {
            every { geoCacheRepository.findByKey("geo:london:gb") } returns null
            val londonUK = GeoResult(
                id = 2643743, name = "London", latitude = 51.51, longitude = -0.13,
                country = "United Kingdom", countryCode = "GB", population = 8_000_000
            )
            val londonCA = GeoResult(
                id = 6058560, name = "London", latitude = 42.98, longitude = -81.25,
                country = "Canada", countryCode = "CA", population = 400_000
            )
            every { openMeteoClient.geocode("London", "GB") } returns GeocodingResponse(
                results = listOf(
                    londonUK,
                    londonCA
                )
            )
            every { geoCacheRepository.save(any(), any()) } returns Unit

            val result = geocodingService.resolveCity("London", "GB")

            result.countryCode shouldBe "GB"
            result.geonameId shouldBe 2643743
        }

        "filters by full country name when country is provided" {
            every { geoCacheRepository.findByKey("geo:london:united kingdom") } returns null
            val londonUK = GeoResult(
                id = 2643743, name = "London", latitude = 51.51, longitude = -0.13,
                country = "United Kingdom", countryCode = "GB", population = 8_000_000
            )
            val londonCA = GeoResult(
                id = 6058560, name = "London", latitude = 42.98, longitude = -81.25,
                country = "Canada", countryCode = "CA", population = 400_000
            )
            every { openMeteoClient.geocode("London", "United Kingdom") } returns GeocodingResponse(
                results = listOf(
                    londonUK,
                    londonCA
                )
            )
            every { geoCacheRepository.save(any(), any()) } returns Unit

            val result = geocodingService.resolveCity("London", "United Kingdom")

            result.country shouldBe "United Kingdom"
            result.countryCode shouldBe "GB"
            result.geonameId shouldBe 2643743
        }

        "selects result with highest population" {
            every { geoCacheRepository.findByKey("geo:moscow") } returns null
            val small = GeoResult(
                id = 1, name = "Moscow", latitude = 55.0, longitude = 37.0,
                country = "Russia", countryCode = "RU", population = 10_000
            )
            val big = GeoResult(
                id = 524901, name = "Moscow", latitude = 55.75, longitude = 37.62,
                country = "Russia", countryCode = "RU", population = 12_000_000
            )
            every { openMeteoClient.geocode("Moscow", null) } returns GeocodingResponse(results = listOf(small, big))
            every { geoCacheRepository.save(any(), any()) } returns Unit

            val result = geocodingService.resolveCity("Moscow", null)

            result.population shouldBe 12_000_000
            result.geonameId shouldBe 524901
        }

        "throws CityNotFoundException when country filter eliminates all results" {
            every { geoCacheRepository.findByKey("geo:london:fr") } returns null
            val londonUK = GeoResult(
                id = 2643743, name = "London", latitude = 51.51, longitude = -0.13,
                country = "United Kingdom", countryCode = "GB", population = 8_000_000
            )
            every { openMeteoClient.geocode("London", "FR") } returns GeocodingResponse(results = listOf(londonUK))

            shouldThrow<CityNotFoundException> {
                geocodingService.resolveCity("London", "FR")
            }
        }
    }
})
