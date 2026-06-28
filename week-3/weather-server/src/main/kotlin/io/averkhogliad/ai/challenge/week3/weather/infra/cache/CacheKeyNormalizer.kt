package io.averkhogliad.ai.challenge.week3.weather.infra.cache

object CacheKeyNormalizer {
    private fun normalize(s: String): String = s.trim().lowercase()

    fun geoKey(city: String, country: String?): String =
        "geo:${normalize(city)}${country?.let { ":${normalize(it)}" } ?: ""}"

    fun currentWeatherKey(geonameId: Long): String =
        "current:$geonameId"

    fun forecastKey(geonameId: Long, days: Int): String =
        "forecast:$geonameId:$days"
}
