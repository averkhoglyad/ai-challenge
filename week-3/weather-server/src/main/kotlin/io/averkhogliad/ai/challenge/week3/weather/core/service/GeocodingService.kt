package io.averkhogliad.ai.challenge.week3.weather.core.service

import io.averkhogliad.ai.challenge.week3.weather.core.exception.CityNotFoundException
import io.averkhogliad.ai.challenge.week3.weather.core.geocoding.CityResolver
import io.averkhogliad.ai.challenge.week3.weather.core.model.CityInfo
import io.averkhogliad.ai.challenge.week3.weather.core.model.GeoCacheEntry
import io.averkhogliad.ai.challenge.week3.weather.core.repository.GeoCacheRepository
import io.averkhogliad.ai.challenge.week3.weather.core.validation.WeatherInputValidator
import io.averkhogliad.ai.challenge.week3.weather.infra.cache.CacheKeyNormalizer
import io.averkhogliad.ai.challenge.week3.weather.infra.client.GeoResult
import io.averkhogliad.ai.challenge.week3.weather.infra.client.OpenMeteoProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class GeocodingService(
    private val openMeteoClient: OpenMeteoProvider,
    private val geoCacheRepository: GeoCacheRepository
) : CityResolver {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun resolveCity(city: String, country: String?): CityInfo {
        WeatherInputValidator.validateCity(city)
        WeatherInputValidator.validateCountry(country)

        val cacheKey = CacheKeyNormalizer.geoKey(city, country)

        geoCacheRepository.findByKey(cacheKey)
            ?.let { cached ->
                logger.debug("[CACHE] Geo HIT for key: $cacheKey")
                return cached.toCityInfo()
            }

        logger.debug("[CACHE] Geo MISS or EXPIRED for key: $cacheKey, querying Open-Meteo")

        val response = openMeteoClient.geocode(city, country)

        val results = response.results.orEmpty()
        if (results.isEmpty()) {
            throw CityNotFoundException(city, country)
        }

        val bestMatch = selectBestMatch(results, country)
            ?: throw CityNotFoundException(city, country)

        val entry = bestMatch.toGeoCacheEntry(results.size)
        geoCacheRepository.save(cacheKey, entry)

        return bestMatch.toCityInfo()
    }

    private fun selectBestMatch(results: List<GeoResult>, country: String?): GeoResult? =
        results
            .filter { result -> country == null || result.matchesCountry(country) }
            .maxByOrNull { it.population ?: 0 }

    private fun GeoResult.matchesCountry(country: String): Boolean =
        countryCode.equals(country, ignoreCase = true) || this.country.equals(country, ignoreCase = true)
}

private fun GeoResult.toCityInfo() = CityInfo(
    name = name,
    country = country,
    countryCode = countryCode,
    latitude = latitude,
    longitude = longitude,
    geonameId = id,
    admin1 = admin1,
    population = population?.toInt()
)

private fun GeoResult.toGeoCacheEntry(alternativesCount: Int) = GeoCacheEntry(
    name = name,
    country = country,
    countryCode = countryCode,
    latitude = latitude,
    longitude = longitude,
    geonameId = id,
    admin1 = admin1,
    population = population?.toInt(),
    alternativesCount = alternativesCount
)

private fun GeoCacheEntry.toCityInfo() = CityInfo(
    name = name,
    country = country,
    countryCode = countryCode,
    latitude = latitude,
    longitude = longitude,
    geonameId = geonameId,
    admin1 = admin1,
    population = population
)
